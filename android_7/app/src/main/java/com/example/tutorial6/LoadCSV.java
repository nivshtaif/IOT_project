package com.example.tutorial6;


import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import com.opencsv.CSVReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;

import java.util.List;


public class LoadCSV extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_csv);
        Button BackButton = (Button) findViewById(R.id.button_back);
        Button OpenButton = (Button) findViewById(R.id.open_csv);
        Spinner csv_spinner = findViewById(R.id.csv_spinner);
        String path = "/storage/self/primary/Terminal/DataSources/";
        String[] arraySpinner = getFiles(path);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, arraySpinner);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        csv_spinner.setAdapter(adapter);


        BackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClickBack();
            }
        });

        csv_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                // your code here
                String spinner_filename = csv_spinner.getSelectedItem().toString();
//                csv_spinner.invalidate();
                LineChart lineChart = (LineChart) findViewById(R.id.line_chart);
                ArrayList<String[]> csvData = new ArrayList<>();
//                csvData = (ArrayList<String[]>) csvData.subList(6, csvData.size());
                csvData = CsvRead(String.format("/storage/self/primary/Terminal/DataSources/" + spinner_filename));
//                LineDataSet lineDataSet0 =  new LineDataSet(DataValues(csvData, 1),"Xt"); // x
//                LineDataSet lineDataSet1 =  new LineDataSet(DataValues(csvData, 2),"Yt"); // y
//                LineDataSet lineDataSet2 =  new LineDataSet(DataValues(csvData, 3),"Zt"); // z
                LineDataSet lineDataSetN =  new LineDataSet(DataValuesN(csvData),"Nt"); // N

                ArrayList<ILineDataSet> dataSets = new ArrayList<>();

//                lineDataSet0.setColor(Color.RED);
//                lineDataSet0.setCircleColor(Color.RED);
//                lineDataSet1.setColor(Color.GREEN);
//                lineDataSet1.setCircleColor(Color.GREEN);
//                lineDataSet2.setColor(Color.BLUE);
//                lineDataSet2.setCircleColor(Color.BLUE);
                lineDataSetN.setColor(Color.BLACK);
                lineDataSetN.setCircleColor(Color.BLACK);

//                dataSets.add(lineDataSet0);
//                dataSets.add(lineDataSet1);
//                dataSets.add(lineDataSet2);
                dataSets.add(lineDataSetN);

                LineData data = new LineData(dataSets);
                lineChart.setData(data);
                lineChart.invalidate();


            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // your code here
            }

        });

        OpenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClickBack();
            }
        });

    }

    public String[] getFiles(String path) {

        File directory = new File(path);
        File[] files = directory.listFiles();
        String fileList[] = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            fileList[i] = files[i].getName();
        }
        return fileList;
    }


    private void ClickBack(){
        finish();

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

    private ArrayList<Entry> DataValues(ArrayList<String[]> csvData, int label){
        ArrayList<Entry> dataVals = new ArrayList<Entry>();
        int i = 7;
        while (i < csvData.size())
        {
            dataVals.add(new Entry(Float.parseFloat(csvData.get(i)[0]), Float.parseFloat(csvData.get(i)[label])));
            i++;
        }
        return dataVals;
    }

    private ArrayList<Entry> DataValuesN(ArrayList<String[]> csvData){
        ArrayList<Entry> dataVals = new ArrayList<Entry>();
        int i = 7;
        while (i < csvData.size())
        {
            float acc_x = (float) Math.pow(Float.parseFloat(csvData.get(i)[1]), 2);
            float acc_y = (float) Math.pow(Float.parseFloat(csvData.get(i)[2]), 2);
            float acc_z = (float) Math.pow(Float.parseFloat(csvData.get(i)[3]), 2);
            float N = (float) Math.sqrt(acc_x + acc_y +acc_z);

            dataVals.add(new Entry(Float.parseFloat(csvData.get(i)[0]), N));
            i++;
        }
        return dataVals;
    }


}