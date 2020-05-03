package com.jarvis.pulllayout;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private PullableLayout pullableLayout;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<String> list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pullableLayout = findViewById(R.id.pullLayout);
        listView = findViewById(R.id.listView);
        list = new ArrayList<>();
        for (int i = 0; i < 15; i++) list.add(i + "");
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, list);
        listView.setAdapter(adapter);
        pullableLayout.setPullEventListener(new PullableLayout.PullEventListener() {
            @Override
            public void onRefresh() {
                list.add("refreshed");
                adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, list);
                listView.setAdapter(adapter);
            }

            @Override
            public void onLoad() {
                for (int i = 0; i < 15; i++) list.add(i + 15 + "");
                adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, list);
                listView.setAdapter(adapter);
            }
        });
    }
}
