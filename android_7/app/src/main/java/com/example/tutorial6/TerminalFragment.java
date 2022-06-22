package com.example.tutorial6;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener, AdapterView.OnItemSelectedListener {
    String timeStamp;

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        String text = adapterView.getItemAtPosition(i).toString();
        Toast.makeText(adapterView.getContext(), text, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;

    private TextView receiveText;
    private TextView sendText;
    private TextUtil.HexWatcher hexWatcher;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;

    LineChart mpLineChart;
    LineDataSet lineDataSet0;
    LineDataSet lineDataSet1;
    LineDataSet lineDataSet2;
    LineDataSet lineDataSetN;
    ArrayList<ILineDataSet> dataSets = new ArrayList<>();
    LineData data;

    int flag = 0;

    int startIndex = 0;
    int stopIndex = 1;

    float rest_threshold = (float) 9.8;
    float walk_threshold = (float) 11;
    float run_threshold = (float) 17;
    float jump_threshold = (float) 25;

    int walk_counter = 0;
    int run_counter = 0;
    int jump_counter = 0;

    float sum_jump_time = 0;
    float sum_walk_time = 0;
    float sum_run_time = 0;
    float sum_rest_time = 0;

    TextView walk_time_counter_txt;
    TextView run_time_counter_txt;
    TextView jump_time_counter_txt;

    TextView walk_counter_txt;
    TextView run_counter_txt;
    TextView jump_counter_txt;

    float training_time;
    TextView dis_walk, dis_run;
    PieChart pieChart;
    float walk_dis;
    float run_dis;

    //
    AlertDialog dialog;
    AlertDialog dialog2;
    AlertDialog dialog3;



    int session;
    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);

//        Spinner spinner = view.findViewById(R.id.spinner);
//        ArrayAdapter<CharSequence> arrayAdapter = ArrayAdapter.createFromResource(view.getContext(), R.array.pace, android.R.layout.simple_spinner_item);
//        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//        spinner.setAdapter(arrayAdapter);
//        spinner.setOnItemSelectedListener(this);

        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        sendText = view.findViewById(R.id.send_text);
        hexWatcher = new TextUtil.HexWatcher(sendText);
        hexWatcher.enable(hexEnabled);
        sendText.addTextChangedListener(hexWatcher);
        sendText.setHint(hexEnabled ? "HEX mode" : "");

        View sendBtn = view.findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));

        mpLineChart = (LineChart) view.findViewById(R.id.line_chart);
//        lineDataSet0 =  new LineDataSet(emptyDataValues(), "Xt");
//        lineDataSet1 =  new LineDataSet(emptyDataValues(), "Yt");
//        lineDataSet2 =  new LineDataSet(emptyDataValues(), "Zt");
        lineDataSetN =  new LineDataSet(emptyDataValues(), "Nt");

//        lineDataSet0.setColor(Color.RED);
//        lineDataSet0.setCircleColor(Color.RED);
//        lineDataSet1.setColor(Color.BLUE);
//        lineDataSet1.setCircleColor(Color.BLUE);
//        lineDataSet2.setColor(Color.GREEN);
//        lineDataSet2.setCircleColor(Color.GREEN);
        lineDataSetN.setColor(Color.BLACK);
        lineDataSetN.setCircleColor(Color.BLACK);


//        dataSets.add(lineDataSet0);
//        dataSets.add(lineDataSet1);
//        dataSets.add(lineDataSet2);
        dataSets.add(lineDataSetN);

        data = new LineData(dataSets);

        mpLineChart.setData(data);
        mpLineChart.invalidate();


        Button buttonClear = (Button) view.findViewById(R.id.button1);
        Button buttonCsvShow = (Button) view.findViewById(R.id.button2);
        Button buttonStart = (Button) view.findViewById(R.id.StartBtn);
//        Button buttonStop = (Button) view.findViewById(R.id.StopBtn);
        Button buttonSave = (Button) view.findViewById(R.id.SaveBtn);
//        Button buttonReset = (Button) view.findViewById(R.id.ResetBtn);

        walk_counter_txt = (TextView) view.findViewById(R.id.walk_count);
        walk_counter_txt.setText(String.valueOf(0));

        walk_time_counter_txt = (TextView) view.findViewById(R.id.walk_timer);
        walk_time_counter_txt.setText(String.valueOf(0));

        run_counter_txt = (TextView) view.findViewById(R.id.run_count);
        run_counter_txt.setText(String.valueOf(0));

        run_time_counter_txt = (TextView) view.findViewById(R.id.run_timer);
        run_time_counter_txt.setText(String.valueOf(0));

        jump_counter_txt = (TextView) view.findViewById(R.id.jump_count);
        jump_counter_txt.setText(String.valueOf(0));

        jump_time_counter_txt = (TextView) view.findViewById(R.id.jump_timer);
        jump_time_counter_txt.setText(String.valueOf(0));

        AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext()); // Should be this
        builder.setTitle("Save");

        AlertDialog.Builder builder2 = new AlertDialog.Builder(view.getContext()); // Should be this
        builder2.setTitle("Welcome");

        AlertDialog.Builder builder3 = new AlertDialog.Builder(view.getContext()); // Should be this
        builder3.setTitle("Training Report");


        View view2 = inflater.inflate(R.layout.layout_dialog, null);
        View view3 = inflater.inflate(R.layout.welcome, null);
        View view4 = inflater.inflate(R.layout.training_report, null);

        // Link those objects with their respective
        // id's that we have given in .XML file
        dis_walk = view4.findViewById(R.id.dis_walk);
        dis_run = view4.findViewById(R.id.dis_run);

        EditText CSV_Name = view2.findViewById(R.id.CSV_Name);
        EditText Pace_counter = view2.findViewById(R.id.Pace_counter);
        Button PopUpSave = view2.findViewById(R.id.PopUpSave);
        Button PopUpBack = view2.findViewById(R.id.PopUpBack);
        Spinner spinner = view2.findViewById(R.id.spinner);

        Button WelcomeStart = view3.findViewById(R.id.welStart);
        Button WelcomeBack = view3.findViewById(R.id.welBack);
        Spinner Welcomespinner = view3.findViewById(R.id.spinner);

        ArrayAdapter<CharSequence> arrayAdapter = ArrayAdapter.createFromResource(view.getContext(), R.array.pace, android.R.layout.simple_spinner_item);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(arrayAdapter);
        spinner.setOnItemSelectedListener(this);

        Welcomespinner.setAdapter(arrayAdapter);
        Welcomespinner.setOnItemSelectedListener(this);

        builder2.setView(view3);
        dialog2 = builder2.create();
        if (flag==0){
            dialog2.show();
        }

        builder3.setView(view4);
        dialog3 = builder3.create();
        if (flag==0){
            dialog3.show();
        }

        buttonClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Toast.makeText(getContext(),"Clear",Toast.LENGTH_SHORT).show();
                startIndex = GetIndex();
                stopIndex = startIndex + 1;
                walk_counter = 0;
                run_counter = 0;
                jump_counter = 0;
                sum_walk_time = 0;
                sum_run_time = 0;
                sum_jump_time = 0;

//                Date date = new Date();
//                timeStamp = new SimpleDateFormat("ddMMyyyy HH:mm").format(Calendar.getInstance().getTime());
                LineData data = mpLineChart.getData();
                ILineDataSet set0 = data.getDataSetByIndex(0);
//                ILineDataSet set1 = data.getDataSetByIndex(1);
//                ILineDataSet set2 = data.getDataSetByIndex(2);
                data.getDataSetByIndex(0);
//                data.getDataSetByIndex(1);
//                data.getDataSetByIndex(2);
                while(set0.removeLast()){}
                mpLineChart.setAutoScaleMinMaxEnabled(true);
//                while(set1.removeLast()){}
//                while(set2.removeLast()){}

            }
        });

//        buttonSave.setVisibility(View.INVISIBLE);
//
//        if (sum_walk_time > 3){
//            buttonSave.setVisibility(View.VISIBLE);
//        }





        buttonCsvShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OpenLoadCSV();

            }
        });

        buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getContext(),"Start Counting Steps",Toast.LENGTH_SHORT).show();
//                ArrayList<String[]> csvData = new ArrayList<>();
//                csvData = CsvRead("/storage/self/primary/Terminal/data.csv");
//                startIndex = csvData.size();
//                startIndex = (int) data.getXMax();
                startIndex = GetIndex();
                walk_counter = 0;
                run_counter = 0;
                jump_counter = 0;

                sum_walk_time = 0;
                sum_run_time = 0;
                sum_jump_time = 0;

                timeStamp = new SimpleDateFormat("dd-MM-yyyy_HHmm").format(Calendar.getInstance().getTime());

            }
        });



        PopUpSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                String file_name = CSV_Name.getText().toString();
//                String pace_counter = Pace_counter.getText().toString();
//                String spinner_mode = spinner.getSelectedItem().toString();
//                String mode = spinner.getTransitionName();
                OpenSaveCSV("/sdcard/csv_dir/", String.valueOf(walk_counter), String.valueOf(run_counter), String.valueOf(jump_counter));
//                walk_counter = 0;
//                run_counter = 0;
//                jump_counter = 0;

//                if (spinner_mode.equals("Walk")){
//                    session = 1;
//                    flag = 1;
//                }
//                else {
//                    session = 2;
//                    flag = 1;
//                }
                dialog.dismiss();
//
//                builder3.setView(view4);
//                dialog3 = builder3.create();
//                dialog3.show();



            }
        });
//CHECKKKKKKK
        builder.setView(view2);
        dialog = builder.create();

        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<Entry> dataVals = new ArrayList<Entry>();
                ArrayList<String[]> csvData = new ArrayList<>();
                csvData = CsvRead("/storage/self/primary/Terminal/data.csv");
                stopIndex = csvData.size();
                dialog.show();

            }
        });

        PopUpBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        WelcomeStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                String file_name = CSV_Name.getText().toString();
//                String pace_counter = Pace_counter.getText().toString();
                String spinner_mode = spinner.getSelectedItem().toString();
//                String mode = spinner.getTransitionName();
//                OpenSaveCSV("/sdcard/csv_dir/", String.valueOf(walk_counter), String.valueOf(run_counter), String.valueOf(jump_counter));
//                walk_counter = 0;
//                run_counter = 0;
//                jump_counter = 0;

                if (spinner_mode.equals("Walk")){
                    session = 1;
                    flag = 1;
                }
                else {
                    session = 2;
                    flag = 1;
                }
                dialog2.dismiss();

            }
        });

//        WelcomeSave.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
////                String file_name = CSV_Name.getText().toString();
////                String pace_counter = Pace_counter.getText().toString();
//                String spinner_mode = spinner.getSelectedItem().toString();
////                String mode = spinner.getTransitionName();
////                OpenSaveCSV("/sdcard/csv_dir/", String.valueOf(walk_counter), String.valueOf(run_counter), String.valueOf(jump_counter));
////                walk_counter = 0;
////                run_counter = 0;
////                jump_counter = 0;
//
//                if (spinner_mode.equals("Walk")){
//                    session = 1;
//                    flag = 1;
//                }
//                else {
//                    session = 2;
//                    flag = 1;
//                }
//                dialog.dismiss();
//
//            }
//        });

    //        buttonStop.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                ArrayList<String[]> csvData = new ArrayList<>();
//                csvData = CsvRead("/storage/self/primary/Terminal/data.csv");
//                stopIndex = csvData.size();
//                stopIndex = (int) data.getXMax();
//                System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
//                System.out.println(stopIndex);
//
//            }
//        });


//        buttonReset.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                startIndex = (int) data.getXMax();
//
//            }
//        });

        return view;
    }


    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
        menu.findItem(R.id.hex).setChecked(hexEnabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id == R.id.hex) {
            hexEnabled = !hexEnabled;
            sendText.setText("");
            hexWatcher.enable(hexEnabled);
            sendText.setHint(hexEnabled ? "HEX mode" : "");
            item.setChecked(hexEnabled);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private String[] clean_str(String[] stringsArr){
         for (int i = 0; i < stringsArr.length; i++)  {
             stringsArr[i]=stringsArr[i].replaceAll(" ","");
        }


        return stringsArr;
    }
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if(hexEnabled) {
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receiveText.append(spn);
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] message) {
        if(hexEnabled) {
            receiveText.append(TextUtil.toHexString(message) + '\n');
        } else {
            String msg = new String(message);
            long start = System.currentTimeMillis();
            if(newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
                // don't show CR as ^M if directly before LF
                String msg_to_save = msg;
                msg_to_save = msg.replace(TextUtil.newline_crlf, TextUtil.emptyString);
                // check message length
                if (msg_to_save.length() > 1){
                // split message string by ',' char
                String[] parts = msg_to_save.split(",");
                // function to trim blank spaces
                parts = clean_str(parts);

                    // saving data to csv
                try {
//                    long start = System.currentTimeMillis();

                    // create new csv unless file already exists
                    File file = new File("/storage/self/primary/Terminal/");
                    file.mkdirs();
                    String csv = "/storage/self/primary/Terminal/data.csv";
//                    String temp_name = "";
//                    String temp = String.format("/storage/self/primary/Terminal/%s", temp_name);
                    CSVWriter csvWriter = new CSVWriter(new FileWriter(csv,true));

                    // parse string values, in this case [0] is tmp & [1] is count (t)
                    String row[]= new String[]{parts[0],parts[1], parts[2], parts[3]};
                    csvWriter.writeNext(row);
                    csvWriter.close();
                    // add received values to line dataset for plotting the linechart
//                    data.addEntry(new Entry(Integer.valueOf(parts[3]),Float.parseFloat(parts[0])),0);
//                    data.addEntry(new Entry(Integer.valueOf(parts[3]),Float.parseFloat(parts[1])),1);
//                    data.addEntry(new Entry(Integer.valueOf(parts[3]),Float.parseFloat(parts[2])),2);



                    float acc_x = (float) Math.pow(Float.parseFloat(parts[0]), 2);
                    float acc_y = (float) Math.pow(Float.parseFloat(parts[1]), 2);
                    float acc_z = (float) Math.pow(Float.parseFloat(parts[2]), 2);
                    float N = (float) Math.sqrt(acc_x + acc_y +acc_z);
                    if (session == 1){
                        jump_counter = 10000000;
                    }

//                    #JUMP = 25
//                    #REST = 9.8
                    if (N > rest_threshold-0.2 && N < rest_threshold+0.2) {
                        long finish = System.currentTimeMillis();
                        sum_rest_time += (float) (finish - start)/1000;
                        sum_rest_time += 0.5;
                    }

                    else if (N > jump_threshold) {
                        jump_counter = jump_counter + 1;
                        long finish = System.currentTimeMillis();
                        sum_jump_time += (float) (finish - start)/1000;
                        sum_jump_time += 0.5;
                    }
                    else if (N > walk_threshold && N < run_threshold) {
                        walk_counter = walk_counter + 1;
                        long finish = System.currentTimeMillis();
                        sum_walk_time += (float) (finish - start)/1000;
                        sum_walk_time += 0.5;
                    }
                    else if (N > run_threshold && N < jump_threshold) {
                        run_counter = run_counter + 1;
                        long finish = System.currentTimeMillis();
                        sum_run_time += (float) (finish - start)/1000;
                        sum_run_time += 0.5;
                    }

                    walk_time_counter_txt.setText(String.valueOf(sum_walk_time));
                    run_time_counter_txt.setText(String.valueOf(sum_run_time));
                    jump_time_counter_txt.setText(String.valueOf(sum_jump_time));

                    walk_counter_txt.setText(String.valueOf(walk_counter));
                    run_counter_txt.setText(String.valueOf(run_counter));
                    jump_counter_txt.setText(String.valueOf(jump_counter));

                    training_time = sum_run_time + sum_walk_time;
                    // Set the percentage of language used
                    int walk_part = (int) (100*(sum_walk_time/training_time));
                    int run_part = (int) (100*(sum_run_time/training_time));
                    dis_walk.setText(Integer.toString(walk_part));
                    dis_run.setText(Integer.toString(run_part));
                    // Set the data and color to the pie chart
                    showPieChart(walk_part, run_part);

                    walk_dis = (float) (walk_counter * (0.74));
                    run_dis = (float) (walk_counter * (1.1));
                    dis_walk.setText(String.valueOf(walk_dis));
                    dis_run.setText(String.valueOf(run_dis));


                    data.addEntry(new Entry(Integer.valueOf(parts[3]),N),0);

//                    lineDataSet0.notifyDataSetChanged(); // let the data know a dataSet changed
//                    lineDataSet1.notifyDataSetChanged(); // let the data know a dataSet changed
//                    lineDataSet2.notifyDataSetChanged(); // let the data know a dataSet changed
                    lineDataSetN.notifyDataSetChanged(); // let the data know a dataSet changed

                    mpLineChart.notifyDataSetChanged(); // let the chart know it's data changed
                    mpLineChart.invalidate(); // refresh


                } catch (IOException e) {
                    e.printStackTrace();
                }}

                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                // send msg to function that saves it to csv
                // special handling if CR and LF come in separate fragments
                if (pendingNewline && msg.charAt(0) == '\n') {
                    Editable edt = receiveText.getEditableText();
                    if (edt != null && edt.length() > 1)
                        edt.replace(edt.length() - 2, edt.length(), "");
                }
                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            }
            receiveText.append(TextUtil.toCaretString(msg, newline.length() != 0));
        }
    }

    private void showPieChart(int walk_part, int run_part)
    {

        ArrayList<PieEntry> pieEntries = new ArrayList<>();
        String label = "type";

        //initializing data
        Map<String, Integer> typeAmountMap = new HashMap<>();
        typeAmountMap.put("Walking",walk_part);
        typeAmountMap.put("Running",run_part);

        //initializing colors for the entries
        ArrayList<Integer> colors = new ArrayList<>();
        colors.add(Color.parseColor("#FF2D00"));
        colors.add(Color.parseColor("#25AC04"));

        //input data and fit data into pie chart entry
        for(String type: typeAmountMap.keySet()){
            pieEntries.add(new PieEntry(typeAmountMap.get(type).floatValue(), type));
        }

        //collecting the entries with label name
        PieDataSet pieDataSet = new PieDataSet(pieEntries,label);
        //setting text size of the value
        pieDataSet.setValueTextSize(12f);
        //providing color list for coloring different entries
        pieDataSet.setColors(colors);
        //grouping the data set from entry to chart
        PieData pieData = new PieData(pieDataSet);
        //showing the value of the entries, default true if not set
        pieData.setDrawValues(true);

        pieChart.setData(pieData);
        pieChart.invalidate();
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        try {
        receive(data);}
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

    private ArrayList<Entry> emptyDataValues()
    {
        ArrayList<Entry> dataVals = new ArrayList<Entry>();
        return dataVals;
    }

    private void OpenLoadCSV(){
        Intent intent = new Intent(getContext(),LoadCSV.class);
        startActivity(intent);
    }


    private ArrayList<String[]> CsvRead(String path){
        ArrayList<String[]> CsvData = new ArrayList<>();
        try {
            File file = new File(path);
            CSVReader reader = new CSVReader(new FileReader(file));
            String[]nextline;
            while((nextline = reader.readNext())!= null){
                if(nextline != null){
                    CsvData.add(nextline);

                }
            }

        }catch (Exception e){}
        return CsvData;
    }

    private int GetIndex()
    {
        ArrayList<String[]> csvData = new ArrayList<>();
        csvData = CsvRead("/storage/self/primary/Terminal/data.csv");
        int index = csvData.size();
        return index;
    }

    // Save CSV
//    private void OpenSaveCSV(String file_name, String Paces, String spinnerMode, int endIndex){
//
//        try{
//            ArrayList<Entry> dataVals = new ArrayList<Entry>();
//            ArrayList<String[]> csvData = new ArrayList<>();
//            csvData = CsvRead("/storage/self/primary/Terminal/data.csv");
//            File file = new File("/storage/self/primary/Terminal/DataSources");
//            file.mkdirs();
//            String temp_csv = String.format("/storage/self/primary/Terminal/DataSources/" + file_name);
//            String[] row1 = new String[]{"NAME:", file_name};
//            String[] row2 = new String[]{"EXPERIMENT TIME:", timeStamp};
//            String[] row3 = new String[]{"ACTIVITY TYPE:", spinnerMode};
//            System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" + steps_counter);
//            String[] row4 = new String[]{"COUNT OF ACTUAL STEPS:", Paces, "ESTIMATED NUMBER OF STEPS:", String.valueOf(steps_counter)};
//            String[] row5 = new String[]{};
//            String[] row6 = new String[]{"Time [sec]", "ACC X", "ACC Y", "ACC Z"};
//            CSVWriter csvWriter = new CSVWriter(new FileWriter(temp_csv,true));
//            csvWriter.writeNext(row1);
//            csvWriter.writeNext(row2);
//            csvWriter.writeNext(row3);
//            csvWriter.writeNext(row4);
//            csvWriter.writeNext(row5);
//            csvWriter.writeNext(row6);
//
//            int flag = 1;
//            for (int i = startIndex; i <= stopIndex ; i++)
//            {
//                String x = csvData.get(i)[0];
//                String y = csvData.get(i)[1];
//                String z = csvData.get(i)[2];
//                double t;
//                if (flag == 1) { t = Double.parseDouble(csvData.get(i)[3])/1000;}
//                else {t = Double.parseDouble(csvData.get(i)[3])/1000 - flag;}
//                String[] row = new String[]{String.valueOf(t), x, y, z};
////                CSVWriter csvWriter = new CSVWriter(new FileWriter(temp_csv,true));
//                csvWriter.writeNext(row);
//            }
//            csvWriter.close();
//
//        } catch (IOException e) {
//        e.printStackTrace();
//        }
//
//    }

    private void OpenSaveCSV(String path,String walk_txt, String run_txt, String jump_txt){
        try{
            File file = new File(path);
            file.mkdirs();
            String csv = path + "data.csv";
            CSVWriter csvWriter = new CSVWriter(new FileWriter(csv,true));
            String row[]= new String[]{timeStamp,walk_txt,run_txt,jump_txt};
            csvWriter.writeNext(row);
            csvWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }




}
