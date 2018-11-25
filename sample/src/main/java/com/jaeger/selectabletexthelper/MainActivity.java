package com.jaeger.selectabletexthelper;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.jaeger.library.OnSelectListener;
import com.jaeger.library.SelectableTextHelper;

public class MainActivity extends AppCompatActivity {

    private LinearLayout mLLContent;
    private TextView mTvTest_1;
    private TextView mTvTest_2;
    private TextView mTvTest_3;
    private TextView mTvTest_4;
    private TextView mTvTest_5;
    private TextView mTvTest_6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }

    private void initView() {
        mLLContent = (LinearLayout) findViewById(R.id.ll_content);
        mTvTest_1 = (TextView) findViewById(R.id.tv_test_1);
        mTvTest_2 = (TextView) findViewById(R.id.tv_test_2);
        mTvTest_3 = (TextView) findViewById(R.id.tv_test_3);
        mTvTest_4 = (TextView) findViewById(R.id.tv_test_4);
        mTvTest_5 = (TextView) findViewById(R.id.tv_test_5);
        mTvTest_6 = (TextView) findViewById(R.id.tv_test_6);

        // 点击取消显示
        setClick(mTvTest_1);
        setClick(mTvTest_2);
        setClick(mTvTest_3);
        setClick(mTvTest_4);
        setClick(mTvTest_5);
        setClick(mTvTest_6);

        // 长按文本立即弹出选择复制框
        SelectableTextHelper.setTextSelectable(mTvTest_1);
        SelectableTextHelper.setTextSelectable(mTvTest_3);
        SelectableTextHelper.setTextSelectable(mTvTest_5);
        // 长按后，不立即弹出选择复制框，而是点击复制选项后调用showWaitingTextSelector()弹出复制框
        setOnLongClick(mTvTest_2);
        setOnLongClick(mTvTest_4);
        setOnLongClick(mTvTest_6);
    }

    private void setClick(TextView textView) {
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SelectableTextHelper.hideShowingTextSelector();
            }
        });
    }

    private void setOnLongClick(final TextView textView) {
        textView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                SelectableTextHelper.setWaitingHelper(textView);
                showSelectDialog();
                return false;
            }
        });
    }

    private void showSelectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("复制工具")
                .setMessage("要复制文字吗？")
                .setPositiveButton("复制", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SelectableTextHelper.showWaitingTextSelector(true);
                        dialog.dismiss();
                    }
                }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).setCancelable(true);
        builder.create().show();
    }
}
