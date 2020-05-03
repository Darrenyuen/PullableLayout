package com.jarvis.pulllayout;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.Timer;
import java.util.TimerTask;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * yuan
 * 2020/4/17
 **/
public class PullableLayout extends RelativeLayout { //继承ViewGroup需要重写onMeasure(),继承RelativeLayout或其他ViewGroup的子类可以不重写onMeasure()

    private String TAG = this.getClass().getSimpleName();

    private final int INIT = 0;
    private final int RELEASE_TO_REFRESH = 1;
    private final int REFRESHING = 2;
    private final int RELEASE_TO_LOAD = 3;
    private final int LOADING = 4;
    private final int DONE = 5;
    private final int SUCCEED = 7;
    private final int FAIL = 8;

    private int state = INIT;

    //释放刷新/加载的距离
    private float refreshDist = 200;
    private float loadDist = 200;

    //下拉的距离
    private float pullDownY = 0;
    //上拉的距离
    private float pullUpY = 0;

    //按下Y坐标，上一个事件点Y坐标
    private float downY, lastY;
    //回滚速度
    private float MOVE_SPEED = 8;
    //在刷新过程中滑动操作
    private boolean isTouch = false;
    //手指滑动距离与下拉头的滑动距离比，中间会随正切函数变化
    private float radio = 2;

    //下拉头布局
    RelativeLayout header;
    private ImageView pullDownIV;
    private ImageView refreshingIV;
    private ImageView refreshStateIV;
    private TextView refreshStateTV;
    
    //中间布局
    ListView listView;

    //上拉头布局
    RelativeLayout footer;
    private ImageView pullUpIV;
    private ImageView loadingIV;
    private ImageView loadStateIV;
    private TextView loadStateTV;

    //动画
    private RotateAnimation rotateAnimation;
    private RotateAnimation loadAnimation;

    private PullEventListener pullEventListener;

    private TaskTimer taskTimer;

    Handler updateHandler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            // 回弹速度随下拉距离moveDeltaY增大而增大
            MOVE_SPEED = (float) (8 + 5 * Math.tan(Math.PI / 2
                    / getMeasuredHeight() * (pullDownY + Math.abs(pullUpY))));
            if (!isTouch){
                // 正在刷新，且没有往上推的话则悬停，显示"正在刷新..."
                if (state == REFRESHING && pullDownY <= refreshDist)
                {
                    pullDownY = refreshDist;
                    taskTimer.cancel();
                } else if (state == LOADING && -pullUpY <= loadDist)
                {
                    pullUpY = -loadDist;
                    taskTimer.cancel();
                }

            }
            if (pullDownY > 0)
                pullDownY -= MOVE_SPEED;
            else if (pullUpY < 0)
                pullUpY += MOVE_SPEED;
            if (pullDownY < 0) {
                // 已完成回弹
                pullDownY = 0;
                listView.clearAnimation();
                // 隐藏下拉头时有可能还在刷新，只有当前状态不是正在刷新时才改变状态
                if (state != REFRESHING && state != LOADING)
                    changeState(INIT);
                taskTimer.cancel();
                requestLayout();
            }
            if (pullUpY > 0) {
                // 已完成回弹
                pullUpY = 0;
                pullUpIV.clearAnimation();
                // 隐藏上拉头时有可能还在刷新，只有当前状态不是正在刷新时才改变状态
                if (state != REFRESHING && state != LOADING)
                    changeState(INIT);
                taskTimer.cancel();
                requestLayout();
            }
            // 刷新布局,会自动调用onLayout
            requestLayout();
            // 没有拖拉或者回弹完成
            if (pullDownY + Math.abs(pullUpY) == 0)
                taskTimer.cancel();
        }

    };

    public PullableLayout(Context context) {
        this(context, null);
    }

    public PullableLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PullableLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    private void initView(Context context) {
        rotateAnimation = (RotateAnimation) AnimationUtils.loadAnimation(context, R.anim.reverse);
        loadAnimation = (RotateAnimation) AnimationUtils.loadAnimation(context, R.anim.rotate);
        LinearInterpolator lir = new LinearInterpolator();
        rotateAnimation.setInterpolator(lir);
        loadAnimation.setInterpolator(lir);
        taskTimer = new TaskTimer(updateHandler);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        header = (RelativeLayout) getChildAt(0);
        listView = (ListView) getChildAt(1);
        footer = (RelativeLayout) getChildAt(2);

        Log.d(TAG, "onLayout: " + (pullDownY - header.getMeasuredHeight()));
        initView();
        header.layout(0, (int) pullDownY - header.getMeasuredHeight(),
                header.getMeasuredWidth(), (int) pullDownY);
        listView.layout(0, (int) (pullDownY - pullUpY), listView.getMeasuredWidth(),
                listView.getMeasuredHeight() + (int) (pullDownY - pullUpY));
        footer.layout(0, listView.getMeasuredHeight() - (int) pullUpY,
                footer.getMeasuredWidth(), (int) pullUpY + listView.getMeasuredHeight() + footer.getMeasuredHeight());

        refreshDist = header.getChildAt(0).getMeasuredHeight();
        loadDist = footer.getChildAt(0).getMeasuredHeight();
    }

    private void initView() {
        pullDownIV = header.findViewById(R.id.pull_icon);
        refreshingIV = header.findViewById(R.id.refreshing_icon);
        refreshStateIV = header.findViewById(R.id.state_iv);
        refreshStateTV = header.findViewById(R.id.state_tv);

        pullUpIV = footer.findViewById(R.id.pullup_icon);
        loadingIV = footer.findViewById(R.id.loading_icon);
        loadStateIV = footer.findViewById(R.id.loadstate_iv);
        loadStateTV = footer.findViewById(R.id.loadstate_tv);
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        super.dispatchTouchEvent(ev);
//        Log.d(TAG, "dispatchTouchEvent: " + super.dispatchTouchEvent(ev) + " " + ev.getY() + " " + ev.getRawY());
        //可以执行下拉刷新
        //可以执行上拉加载
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downY = ev.getY();
                lastY = downY;
                break;
                //过滤多点触碰
//            case MotionEvent.ACTION_POINTER_DOWN:
//            case MotionEvent.ACTION_POINTER_UP:
//                break;
            //重点
            case MotionEvent.ACTION_MOVE:
                //如果不是多点触碰
                Log.d(TAG, "dispatchTouchEvent: " + pullUpY + " " + pullDownY);
                if ((ev.getY() - downY > 0) && state != LOADING && listView.getChildAt(0).getTop() == 0) {
                    pullUpY = 0;
                    pullDownY = ev.getY() - downY;
//                    pullDownY = pullDownY + (ev.getY() - lastY) / radio;
                    if (pullDownY > refreshDist) changeState(RELEASE_TO_REFRESH);
                    else changeState(INIT);
                } else if (ev.getY() - downY < 0 && state != REFRESHING &&
                        listView.getHeight() >= listView.getChildAt(listView.getLastVisiblePosition() - listView.getFirstVisiblePosition()).getBottom()){
                    pullDownY = 0;
                    pullUpY = downY - ev.getY();
//                    pullUpY = pullUpY + (lastY - ev.getY()) / radio;
                    if (pullUpY > loadDist) changeState(RELEASE_TO_LOAD);
                    else changeState(INIT);
                }
                lastY = ev.getY();
                requestLayout();
                break;
            case MotionEvent.ACTION_UP:
                //如果没有达到刷新或加载的要求则隐藏布局
                if (pullDownY > refreshDist || -pullUpY > loadDist) {
                    isTouch = false;
                }
                if (state == RELEASE_TO_REFRESH) {
                    changeState(REFRESHING);
                    if (pullEventListener != null) pullEventListener.onRefresh();
                    //隐藏头布局
                    refreshFinish(SUCCEED);
                } else if (state == RELEASE_TO_LOAD) {
                    changeState(LOADING);
                    if (pullEventListener != null) pullEventListener.onLoad();
                    //隐藏脚布局
                    loadFinish(SUCCEED);
                }
                hide();
                break;
            default:
                break;
        }
        return true;
    }

    public void refreshFinish(int refreshResult) {
        refreshingIV.clearAnimation();
        refreshingIV.setVisibility(View.GONE);
        switch (refreshResult) {
            case SUCCEED:
                // 刷新成功
                refreshStateIV.setBackground(getResources().getDrawable(R.drawable.succeed));
                refreshStateIV.setVisibility(View.VISIBLE);
                refreshStateTV.setText("刷新成功");
                break;
            case FAIL:
            default:
                // 刷新失败
                refreshStateIV.setBackground(getResources().getDrawable(R.drawable.failed));
                refreshStateIV.setVisibility(View.VISIBLE);
                refreshStateTV.setText("刷新失败");
                break;
        }
        if (pullDownY > 0) {
            // 刷新结果停留1秒
            new Handler() {
                @Override
                public void handleMessage(Message msg)
                {
                    changeState(DONE);
                    hide();
                }
            }.sendEmptyMessageDelayed(0, 1000);
        } else {
            changeState(DONE);
            hide();
        }
    }

    private void loadFinish(int loadResult) {
        refreshingIV.clearAnimation();
        refreshingIV.setVisibility(View.GONE);
        switch (loadResult) {
            case SUCCEED:
                loadStateIV.setBackground(getResources().getDrawable(R.drawable.succeed));
                loadStateIV.setVisibility(View.VISIBLE);
                loadStateTV.setText("加载成功");
                break;
            case FAIL:
                default:
                    loadStateIV.setBackground(getResources().getDrawable(R.drawable.failed));
                    loadStateIV.setVisibility(View.VISIBLE);
                    loadStateTV.setText("加载失败");
                    break;
        }
        if (pullUpY > 0) {
            new Handler() {
                @Override
                public void handleMessage(@NonNull Message msg) {
                    changeState(DONE);
                    hide();
                }
            }.sendEmptyMessageDelayed(0, 1000);
        } else {
            changeState(DONE);
            hide();
        }
    }

    private void hide() {
        taskTimer.schedule(5);
    }

    private void changeState(int state) {
        this.state = state;
        switch (state) {
            case INIT:
                refreshStateIV.setVisibility(View.GONE);
                refreshStateTV.setText(R.string.pull_down);
                pullDownIV.clearAnimation();
                pullDownIV.setVisibility(View.VISIBLE);
                loadingIV.setVisibility(View.GONE);
                loadStateTV.setText(R.string.pull_up);
                pullUpIV.clearAnimation();
                pullUpIV.setVisibility(View.VISIBLE);
                break;
            case RELEASE_TO_REFRESH:
                refreshStateTV.setText(R.string.can_refresh);
                pullDownIV.startAnimation(rotateAnimation);
                break;
            case REFRESHING:
                pullDownIV.clearAnimation();
                pullDownIV.setVisibility(View.GONE);
                refreshingIV.setVisibility(View.VISIBLE);
                refreshingIV.startAnimation(loadAnimation);
                refreshStateTV.setText(R.string.refreshing);
                break;
            case RELEASE_TO_LOAD:
                loadStateTV.setText(R.string.can_load);
                pullUpIV.startAnimation(rotateAnimation);
                break;
            case LOADING:
                pullUpIV.clearAnimation();
                pullUpIV.setVisibility(View.GONE);
                loadingIV.setVisibility(View.VISIBLE);
                loadingIV.startAnimation(loadAnimation);
                loadStateTV.setText(R.string.loading);
                break;
            case DONE:
                break;
        }
    }

    public void setPullEventListener(PullEventListener pullEventListener) {
        this.pullEventListener = pullEventListener;
    }

    public interface PullEventListener{
        void onRefresh();
        void onLoad();
    }

    class TaskTimer {
        private Handler handler;
        private Timer timer;
        private Task mTask;

        public TaskTimer(Handler handler) {
            this.handler = handler;
            timer = new Timer();
        }

        public void schedule(long period) {
            if (mTask != null) {
                mTask.cancel();
                mTask = null;
            }
            mTask = new Task(handler);
            timer.schedule(mTask, 0, period);
        }

        public void cancel() {
            if (mTask != null) {
                mTask.cancel();
                mTask = null;
            }
        }

        class Task extends TimerTask {
            private Handler handler;

            protected Task(Handler handler) {
                super();
                this.handler = handler;
            }

            @Override
            public void run() {
                handler.obtainMessage().sendToTarget();
            }
        }
    }
}
