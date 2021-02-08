package jp.iflink.closed_buster.graph;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;

import com.ekn.gruzer.gaugelibrary.FullGauge;

public class CustomArcGauge extends FullGauge {

    private float sweepAngle = 240;
    private float startAngle = 150;
    private float gaugeBGWidth = 20f;

    public CustomArcGauge(Context context) {
        super(context);
        init();
    }

    public CustomArcGauge(Context context, AttributeSet attrs) {
        super(context, attrs);
        sweepAngle = 180;
        startAngle = 180;
        init();
    }

    public CustomArcGauge(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public CustomArcGauge(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        getGaugeBackGround().setStrokeWidth(gaugeBGWidth);
        getGaugeBackGround().setStrokeCap(Paint.Cap.ROUND);
        getGaugeBackGround().setColor(Color.parseColor("#D6D6D6"));
        getTextPaint().setTextSize(0f);
        //getTextPaint().setTextSize(35f);
        //setPadding(5f);
        setPadding(20f);
        setSweepAngle(sweepAngle);
        setStartAngle(startAngle);
    }

    protected void drawValuePoint(Canvas canvas) {
        //no point
    }
}

