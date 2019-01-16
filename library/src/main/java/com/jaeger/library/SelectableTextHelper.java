package com.jaeger.library;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.PopupWindow;
import android.widget.TextView;

/**
 * Created by Jaeger on 16/8/30.
 * <p>
 * Email: chjie.jaeger@gmail.com
 * GitHub: https://github.com/laobie
 */

public class SelectableTextHelper {
    private static int COLOR_SELECTED = 0x404086F8;
    private static int COLOR_HANDLE = 0xFF4086F8;
    private final static int DEFAULT_SELECTION_LENGTH = 1;
    private static final int DEFAULT_SHOW_DURATION = 100;

    private static SelectableTextHelper waitingHelper;
    private CursorHandle mStartHandle;
    private CursorHandle mEndHandle;
    private OperateWindow mOperateWindow;
    private SelectionInfo mSelectionInfo = new SelectionInfo();
    private OnSelectListener mSelectListener;

    private Context mContext;
    private TextView mTextView;
    private Spannable mSpannable;

    private int mTouchX;
    private int mTouchY;

    private int mSelectedColor;
    private int mCursorHandleColor;
    private int mCursorHandleSize;
    private BackgroundColorSpan mSpan;
    private boolean isHideWhenScroll;
    private boolean isCurrentHide = true;

    private ViewTreeObserver.OnPreDrawListener mOnPreDrawListener;
    ViewTreeObserver.OnScrollChangedListener mOnScrollChangedListener;

    public static void setTextSelectable(TextView textView) {
        SelectableTextHelper selectableTextHelper = new SelectableTextHelper.Builder(textView)
                .setSelectedColor(COLOR_SELECTED)
                .setCursorHandleSizeInDp(20)
                .setCursorHandleColor(COLOR_HANDLE)
                .build(true);

        selectableTextHelper.setSelectListener(new OnSelectListener() {
            @Override
            public void onTextSelected(CharSequence content) {

            }
        });
    }

    public static void setWaitingHelper(TextView textView) {
        if (waitingHelper != null) {
            waitingHelper.destroy();
        }
        waitingHelper = new SelectableTextHelper.Builder(textView)
                .setSelectedColor(COLOR_SELECTED)
                .setCursorHandleSizeInDp(20)
                .setCursorHandleColor(COLOR_HANDLE)
                .build(false);

        waitingHelper.setSelectListener(new OnSelectListener() {
            @Override
            public void onTextSelected(CharSequence content) {

            }
        });
    }

    public static void showWaitingTextSelector(boolean allSelected) {
        if (waitingHelper == null) {
            return;
        }
        if (allSelected) {
            waitingHelper.showAllTextSelected();
        } else {
            waitingHelper.showSelectView(waitingHelper.mTouchX, waitingHelper.mTouchY);
        }
    }

    public static void hideShowingTextSelector() {
        if (waitingHelper == null || waitingHelper.isCurrentHide) {
            return;
        }
        waitingHelper.hideSelectView();
        waitingHelper.resetSelectionInfo();
    }

    private SelectableTextHelper(Builder builder, boolean showImmediately) {
        mTextView = builder.mTextView;
        mContext = mTextView.getContext().getApplicationContext();
        mSelectedColor = builder.mSelectedColor;
        mCursorHandleColor = builder.mCursorHandleColor;
        mCursorHandleSize = TextLayoutUtil.dp2px(mContext, builder.mCursorHandleSizeInDp);
        init(showImmediately);
    }

    private void init(boolean showImmediately) {
        mTextView.setText(mTextView.getText(), TextView.BufferType.SPANNABLE);
        if (showImmediately) {
            mTextView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showSelectView(mTouchX, mTouchY);
                    return true;
                }
            });
        }
        mTextView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mTouchX = (int) event.getX();
                mTouchY = (int) event.getY();
                return false;
            }
        });
        mTextView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                mContext = mTextView.getContext().getApplicationContext();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (waitingHelper != null && waitingHelper.mTextView == mTextView) {
                    waitingHelper.mContext = null;
                    waitingHelper = null;
                }
                destroy();
            }
        });

        mOnPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (isHideWhenScroll) {
                    isHideWhenScroll = false;
                    postShowSelectView(DEFAULT_SHOW_DURATION);
                }
                return true;
            }
        };
        mTextView.getViewTreeObserver().addOnPreDrawListener(mOnPreDrawListener);

        mOnScrollChangedListener = new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                if (!isHideWhenScroll && !isCurrentHide) {
                    isHideWhenScroll = true;
                    if (mOperateWindow != null) {
                        mOperateWindow.dismiss();
                    }
                    if (mStartHandle != null) {
                        mStartHandle.dismiss();
                    }
                    if (mEndHandle != null) {
                        mEndHandle.dismiss();
                    }
                }
            }
        };
        mTextView.getViewTreeObserver().addOnScrollChangedListener(mOnScrollChangedListener);

        mOperateWindow = new OperateWindow(mContext);
    }

    private void postShowSelectView(int duration) {
        mTextView.removeCallbacks(mShowSelectViewRunnable);
        if (duration <= 0) {
            mShowSelectViewRunnable.run();
        } else {
            mTextView.postDelayed(mShowSelectViewRunnable, duration);
        }
    }

    private final Runnable mShowSelectViewRunnable = new Runnable() {
        @Override
        public void run() {
            if (isCurrentHide) return;
            if (mOperateWindow != null) {
                mOperateWindow.show();
            }
            if (mStartHandle != null) {
                showCursorHandle(mStartHandle);
            }
            if (mEndHandle != null) {
                showCursorHandle(mEndHandle);
            }
        }
    };

    private void hideSelectView() {
        isCurrentHide = true;
        if (mStartHandle != null) {
            mStartHandle.dismiss();
        }
        if (mEndHandle != null) {
            mEndHandle.dismiss();
        }
        if (mOperateWindow != null) {
            mOperateWindow.dismiss();
        }
    }

    private void resetSelectionInfo() {
        mSelectionInfo.mSelectionContent = null;
        if (mSpannable != null && mSpan != null) {
            mSpannable.removeSpan(mSpan);
            mSpan = null;
        }
    }

    private void showSelectView(int x, int y) {
        if (waitingHelper != null) {
            waitingHelper.destroy();
        }
        hideSelectView();
        resetSelectionInfo();
        if (mStartHandle == null) mStartHandle = new CursorHandle(true);
        if (mEndHandle == null) mEndHandle = new CursorHandle(false);
        if (mOperateWindow == null) mOperateWindow = new OperateWindow(mContext);

        int startOffset = TextLayoutUtil.getPreciseOffset(mTextView, x, y);
        int endOffset = startOffset + DEFAULT_SELECTION_LENGTH;
        if (mTextView.getText() instanceof Spannable) {
            mSpannable = (Spannable) mTextView.getText();
        }
        if (mSpannable == null || startOffset >= mTextView.getText().length()) {
            return;
        }
        selectText(startOffset, endOffset);
        showCursorHandle(mStartHandle);
        showCursorHandle(mEndHandle);
        mOperateWindow.show();

        isCurrentHide = false;
        waitingHelper = this;
    }

    private void showAllTextSelected() {
        hideSelectView();
        isCurrentHide = false;
        if (mStartHandle == null) mStartHandle = new CursorHandle(true);
        if (mEndHandle == null) mEndHandle = new CursorHandle(false);
        if (mOperateWindow == null) mOperateWindow = new OperateWindow(mContext);
        if (mSpannable == null) {
            mSpannable = (Spannable) mTextView.getText();
        }
        selectText(0, mTextView.getText().length());
        showCursorHandle(mStartHandle);
        showCursorHandle(mEndHandle);
        mOperateWindow.show();
    }

    private void showCursorHandle(CursorHandle cursorHandle) {
        Layout layout = mTextView.getLayout();
        int offset = cursorHandle.isLeft ? mSelectionInfo.mStart : mSelectionInfo.mEnd;
        cursorHandle.show((int) layout.getPrimaryHorizontal(offset) - (int) layout.getPrimaryHorizontal(layout.getLineStart(layout.getLineForOffset(offset))), layout.getLineBottom(layout.getLineForOffset(offset)));
    }

    private void selectText(int startPos, int endPos) {
        if (startPos != -1) {
            mSelectionInfo.mStart = startPos;
        }
        if (endPos != -1) {
            mSelectionInfo.mEnd = endPos;
        }
        if (mSelectionInfo.mStart > mSelectionInfo.mEnd) {
            int temp = mSelectionInfo.mStart;
            mSelectionInfo.mStart = mSelectionInfo.mEnd;
            mSelectionInfo.mEnd = temp;
        }

        if (mSpannable != null) {
            if (mSpan == null) {
                mSpan = new BackgroundColorSpan(mSelectedColor);
            }

            if (mSelectionInfo.mEnd > mSpannable.length()) {
                mSelectionInfo.mEnd = mSpannable.length();
            }
            mSelectionInfo.mSelectionContent = mSpannable.subSequence(mSelectionInfo.mStart, mSelectionInfo.mEnd).toString();
            mSpannable.setSpan(mSpan, mSelectionInfo.mStart, mSelectionInfo.mEnd, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            if (mSelectListener != null) {
                mSelectListener.onTextSelected(mSelectionInfo.mSelectionContent);
            }
        }
    }

    public void setSelectListener(OnSelectListener selectListener) {
        mSelectListener = selectListener;
    }

    private void destroy() {
        resetSelectionInfo();
        hideSelectView();
        mStartHandle = null;
        mEndHandle = null;
        mOperateWindow = null;
    }

    /**
     * Operate windows : copy, select all
     */
    private class OperateWindow {

        private PopupWindow mWindow;
        private int[] mTempCoors = new int[2];

        private int mWidth;
        private int mHeight;

        public OperateWindow(final Context context) {
            View contentView = LayoutInflater.from(context).inflate(R.layout.layout_operate_windows, null);
            contentView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            mWidth = contentView.getMeasuredWidth();
            mHeight = contentView.getMeasuredHeight();
            mWindow =
                    new PopupWindow(contentView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
            mWindow.setClippingEnabled(false);

            contentView.findViewById(R.id.tv_copy).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ClipboardManager clip = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    clip.setPrimaryClip(
                            ClipData.newPlainText(mSelectionInfo.mSelectionContent, mSelectionInfo.mSelectionContent));
                    if (mSelectListener != null) {
                        mSelectListener.onTextSelected(mSelectionInfo.mSelectionContent);
                    }
                    SelectableTextHelper.this.resetSelectionInfo();
                    SelectableTextHelper.this.hideSelectView();
                }
            });
            contentView.findViewById(R.id.tv_select_all).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAllTextSelected();
                }
            });
        }


        void show() {
            /*mTextView.getLocationInWindow(mTempCoors);
            Layout layout = mTextView.getLayout();
            int posX = (int) layout.getPrimaryHorizontal(mSelectionInfo.mStart) + mTempCoors[0];
            int posY = layout.getLineTop(layout.getLineForOffset(mSelectionInfo.mStart)) + mTempCoors[1] - mHeight - 16;
            if (posX <= 0) posX = 16;
            if (posY < 0) posY = 16;
            if (posX + mWidth > TextLayoutUtil.getScreenWidth(mContext)) {
                posX = TextLayoutUtil.getScreenWidth(mContext) - mWidth - 16;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mWindow.setElevation(8f);
            }
            mWindow.showAtLocation(mTextView, Gravity.NO_GRAVITY, posX, posY);*/

            //(int) layout.getPrimaryHorizontal(offset) - (int) layout.getPrimaryHorizontal(layout.getLineStart(layout.getLineForOffset(offset)))

            mTextView.getLocationOnScreen(mTempCoors);
            Layout layout = mTextView.getLayout();
            int posX = (int) layout.getPrimaryHorizontal(mSelectionInfo.mStart) - (int) layout.getPrimaryHorizontal(layout.getLineStart(layout.getLineForOffset(mSelectionInfo.mStart))) + mTempCoors[0];
            int posY = layout.getLineTop(layout.getLineForOffset(mSelectionInfo.mStart)) + mTempCoors[1] - mHeight - 16;
            if (posX <= 0) posX = 16;
            if (posY < 0) posY = 16;
            if (posX + mWidth > TextLayoutUtil.getScreenWidth(mContext)) {
                posX = TextLayoutUtil.getScreenWidth(mContext) - mWidth - 16;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mWindow.setElevation(8f);
            }
            mWindow.showAtLocation(mTextView, Gravity.NO_GRAVITY, posX, posY);
        }

        public void dismiss() {
            mWindow.dismiss();
        }

        public boolean isShowing() {
            return mWindow.isShowing();
        }
    }

    private class CursorHandle extends View {

        private PopupWindow mPopupWindow;
        private Paint mPaint;

        private int mCircleRadius = mCursorHandleSize / 2;
        private int mWidth = mCircleRadius * 2;
        private int mHeight = mCircleRadius * 2;
        private int mPadding = 25;
        private boolean isLeft;

        public CursorHandle(boolean isLeft) {
            super(mContext);
            this.isLeft = isLeft;
            mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPaint.setColor(mCursorHandleColor);

            mPopupWindow = new PopupWindow(this);
            mPopupWindow.setClippingEnabled(false);
            mPopupWindow.setWidth(mWidth + mPadding * 2);
            mPopupWindow.setHeight(mHeight + mPadding / 2);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawCircle(mCircleRadius + mPadding, mCircleRadius, mCircleRadius, mPaint);
            if (isLeft) {
                canvas.drawRect(mCircleRadius + mPadding, 0, mCircleRadius * 2 + mPadding, mCircleRadius, mPaint);
            } else {
                canvas.drawRect(mPadding, 0, mCircleRadius + mPadding, mCircleRadius, mPaint);
            }
        }

        private int mAdjustX;
        private int mAdjustY;

        private int mAdjustRawX;
        private int mAdjustRawY;

        private int mCurMotionX;
        private int mCurMotionY;

        private int mCurMotionRawX;
        private int mCurMotionRawY;

        private int mBeforeDragStart;
        private int mBeforeDragEnd;

        private int[] mTempCoors = new int[2];

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:

                    mBeforeDragStart = mSelectionInfo.mStart;
                    mBeforeDragEnd = mSelectionInfo.mEnd;

                    mAdjustX = (int) event.getX();
                    mAdjustY = (int) event.getY();

                    mAdjustRawX = (int) event.getRawX();
                    mAdjustRawY = (int) event.getRawY();

                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mOperateWindow.show();
                    break;
                case MotionEvent.ACTION_MOVE:
                    mOperateWindow.dismiss();

                    this.mCurMotionX = (int) event.getX();
                    this.mCurMotionY = (int) event.getY();

                    this.mCurMotionRawX = (int) event.getRawX();
                    this.mCurMotionRawY = (int) event.getRawY();

                    update(this.mCurMotionX, this.mCurMotionY, this.mCurMotionRawX, this.mCurMotionRawY);

                    break;
            }
            return true;
        }

        private void update(int curX, int curY, int curRawX, int curRawY) {
            //获得TextView在屏幕上的位置
            mTextView.getLocationOnScreen(mTempCoors);

            int oldOffset;
            if (isLeft) {
                oldOffset = mSelectionInfo.mStart;
            } else {
                oldOffset = mSelectionInfo.mEnd;
            }

            curX = (int) (curRawX - mTempCoors[0]);
            //curY = (int) (curRawY - curY - mTempCoors[1] - mTextView.getPaddingBottom() - (mTextView.getLayout().getHeight() * 0.5));
            curY = curRawY - mTempCoors[1];

            int offset = TextLayoutUtil.getHysteresisOffset(mTextView, curX, curY, oldOffset);

            if (offset != oldOffset) {
                resetSelectionInfo();

                if (isLeft) {
                    if (offset > mBeforeDragEnd) {
                        CursorHandle handle = getCursorHandle(false);
                        changeDirection();
                        handle.changeDirection();
                        mBeforeDragStart = mBeforeDragEnd;
                        selectText(mBeforeDragEnd, offset);
                        handle.updateCursorHandle();
                    } else {
                        selectText(offset, -1);
                    }
                    updateCursorHandle();
                } else {
                    if (offset < mBeforeDragStart) {
                        CursorHandle handle = getCursorHandle(true);
                        handle.changeDirection();
                        changeDirection();
                        mBeforeDragEnd = mBeforeDragStart;
                        selectText(offset, mBeforeDragStart);
                        handle.updateCursorHandle();
                    } else {
                        selectText(mBeforeDragStart, offset);
                    }
                    updateCursorHandle();
                }

            }
        }

        private void updateCursorHandle() {
            mTextView.getLocationOnScreen(mTempCoors);

            Layout layout = mTextView.getLayout();

            int targetX;
            int targetY;
            //cursorHandle.show((int) layout.getPrimaryHorizontal(offset) - (int) layout.getPrimaryHorizontal(layout.getLineStart(layout.getLineForOffset(offset))), layout.getLineBottom(layout.getLineForOffset(offset)));

            if (isLeft) {
                targetX = (int) layout.getPrimaryHorizontal(mSelectionInfo.mStart) - (int) layout.getPrimaryHorizontal(layout.getLineStart(layout.getLineForOffset(mSelectionInfo.mStart))) + getExtraX() - mWidth - mPadding;
                mPopupWindow.update(
                        targetX,//(int) layout.getPrimaryHorizontal(mSelectionInfo.mStart) + getExtraX() - mWidth - mPadding,
                        layout.getLineBottom(layout.getLineForOffset(mSelectionInfo.mStart)) + getExtraY() + mTextView.getPaddingTop(),
                        -1,
                        -1);
            } else {
                targetX = (int) layout.getPrimaryHorizontal(mSelectionInfo.mEnd) - (int) layout.getPrimaryHorizontal(layout.getLineStart(layout.getLineForOffset(mSelectionInfo.mStart))) + getExtraX() - mPadding;
                mPopupWindow.update(
                        targetX,//(int) layout.getPrimaryHorizontal(mSelectionInfo.mEnd) + getExtraX() - mPadding,
                        layout.getLineBottom(layout.getLineForOffset(mSelectionInfo.mEnd)) + getExtraY() + mTextView.getPaddingTop(),
                        -1,
                        -1);
            }
        }

        public void show(int x, int y) {
            mTextView.getLocationOnScreen(mTempCoors);
            int offset = isLeft ? mWidth : 0;
            mPopupWindow.showAtLocation(mTextView, Gravity.NO_GRAVITY, x + getExtraX() - offset - mPadding, y + getExtraY() + mTextView.getPaddingTop());
        }

        public int getExtraX() {
            return mTempCoors[0];
        }

        public int getExtraY() {
            return mTempCoors[1];
        }

        private void changeDirection() {
            isLeft = !isLeft;
            invalidate();
        }

        public void dismiss() {
            mPopupWindow.dismiss();
        }
    }

    private void printLog(String msg) {
        Log.e(getClass().getCanonicalName(), msg);
    }

    private CursorHandle getCursorHandle(boolean isLeft) {
        if (mStartHandle.isLeft == isLeft) {
            return mStartHandle;
        } else {
            return mEndHandle;
        }
    }

    public static class Builder {
        private TextView mTextView;
        private int mCursorHandleColor = COLOR_HANDLE;
        private int mSelectedColor = COLOR_SELECTED;
        private float mCursorHandleSizeInDp = 24;

        public Builder(TextView textView) {
            mTextView = textView;
        }

        public Builder setCursorHandleColor(@ColorInt int cursorHandleColor) {
            mCursorHandleColor = cursorHandleColor;
            return this;
        }

        public Builder setCursorHandleSizeInDp(float cursorHandleSizeInDp) {
            mCursorHandleSizeInDp = cursorHandleSizeInDp;
            return this;
        }

        public Builder setSelectedColor(@ColorInt int selectedBgColor) {
            mSelectedColor = selectedBgColor;
            return this;
        }

        public SelectableTextHelper build(boolean showImmediately) {
            return new SelectableTextHelper(this, showImmediately);
        }
    }
}


