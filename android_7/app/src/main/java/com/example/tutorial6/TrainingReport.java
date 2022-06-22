package com.example.tutorial6;

import android.widget.TextView;

import com.github.mikephil.charting.charts.PieChart;

public class TrainingReport {

    // Create the object of TextView and PieChart class
    TextView dis_walk, dis_run;
    PieChart pieChart;


    // Link those objects with their respective
    // id's that we have given in .XML file
    dis_walk = findViewById(R.id.dis_walk);
    dis_run = findViewById(R.id.dis_run);
}
