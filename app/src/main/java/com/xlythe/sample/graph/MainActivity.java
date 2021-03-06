package com.xlythe.sample.graph;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import com.xlythe.math.GraphModule;
import com.xlythe.math.Solver;
import com.xlythe.view.graph.GraphView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private final GraphModule mGraphModule = new GraphModule(new Solver());

    private GraphView mGraphView;
    private EditText mFormulaView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mGraphView = findViewById(R.id.graph);
        mGraphView.setShowInlineNumbers(true); // TODO give option to show all numbers in inline mode
        mGraphView.setShowOutline(false);
        mGraphView.addPanListener(this::invalidateFormula);
        mGraphView.addZoomListener(level -> invalidateFormula());

        mFormulaView = findViewById(R.id.formula);
        mFormulaView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(final Editable s) {
                invalidateFormula();
            }
        });
    }

    private void invalidateFormula() {
        final String formula = mFormulaView.getText().toString();
        if (formula.isEmpty()) {
            return;
        }

        mGraphModule.setDomain(mGraphView.getXAxisMin(), mGraphView.getXAxisMax());
        mGraphModule.setRange(mGraphView.getYAxisMin(), mGraphView.getYAxisMax());
        mGraphModule.updateGraph(formula, result -> {
            mGraphView.clearGraphs();
            mGraphView.addGraph(new GraphView.Graph(formula, 0xff00bcd4, result));
        });
    }
}
