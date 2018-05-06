package bannerview.mylibrary;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.support.annotation.Nullable;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;

/**
 * @desc ${TODD}
 */

public class BannerView extends View implements ViewTreeObserver.OnGlobalLayoutListener {

    private int mDataCount;//总数据长度
    private ArrayList<Bitmap> bitmaps = new ArrayList<>();//保存绘制的图片
    private int mViewHeight;//控件的高
    private int mViewWidth;//控件的宽

    private boolean isFirstDraw = true;
    public boolean mIsBeingDragged = false;//避免触发拖拽事件时，onDraw中执行动画，而不是canvas图片
    private boolean mIsBeingTouch = false;//避免触发点击事件的时候，执行animator.cancel导致currentIndex/scrollAction相关数值发生改变
    private boolean isAutoScrollToRight = false;//只有当scrollAction==AUTO_SCROLL_TO_RIGHT时，才会置为true,当触发点击事件或者animator执行动画完毕才会置为false

    private Paint paint;
    private int currentIndex;// 当前图片的index
    private int tempIndex;//用于临时保存currentIndex的值
    private int nextIndex;//下一张图片的index
    private int preIndex;// 上一张图片的index
    private int enterIndex;//与当前图片共同显示在window的图片的index

    public static final int AUTO_SCROLL_TO_LEFT = 0;//自动向左滑动
    public static final int AUTO_SCROLL_TO_RIGHT = 1;//自动向右滑动
    public static final int DRAG = 2;//预留状态
    public static final int WAIT = 3;//预留状态
    private static final int MIN_FLING_VELOCITY = 400; // dips

    private int defaultScrollAction = AUTO_SCROLL_TO_LEFT;//默认滑动设置
    private int scrollAction = defaultScrollAction;

    private VelocityTracker mVelocityTracker;//用以求瞬时速度
    private int mMinimumVelocity;//Flinging的最小速度
    private int mMaximumVelocity;//Flinging的最大速度
    private int mTouchSlop;//触发手势滑动的最小距离，用以判断是否是点击事件还是拖拽事件

    private float rawX;
    private float lastRawX;
    private float moveX;

    private Bitmap enterBitmap;//与当前图片共同显示在window的图片
    private Bitmap currentBitmap;//当前图片

    private ValueAnimator animator;
    private PropertyValuesHolder propertyValuesHolder;//用于重新设置ValueAnimator的数值
    // 涉及到两个图片之间的显示位置，当defaultScrollAction==AUTO_SCROLL_TO_LEFT时，animatedValue=mViewWidth,显示当前图片，animatedValue=0,显示下一张图片
    // animatedValue相当于距离屏幕左边的距离（无论scrollAction=AUTO_SCROLL_TO_LEFT还是scrollAction=AUTO_SCROLL_TO_RIGHT）
    private float animatedValue;
    private float lastAnimatedValue;//当触发点击事件时，用以临时保存animatedValue的值
    private float turnOverLimitValue;//触发逆向滑动的最小数值
    private long mDelayMillis = 3 * 1000;//延时启动动画的时间
    private long duration = 800;//动画的时间
    private long surplusDuration;//剩余执行动画的时间
    private long surplusDelayMillis;//剩余延迟启动动画的时间
    private long startTime;//开启动画的时间，只在surplusDelayMillis==mDelayMillis才会被赋值
    private long cancelTime;//只有当animator还未开始时，触发了点击事件cancelTime了才会被赋值
    private int mActivePointerId;//手指点击屏幕的时，获取第一个触点的id

    private ItemClickListener listener;

    //    public void setBitmaps(ArrayList<Bitmap> bitmaps) {
    //        this.bitmaps = bitmaps;
    //        mDataCount = bitmaps.size();
    //        invalidate();
    //    }

    public void setCurrentIndex(int currentIndex) {
        this.currentIndex = currentIndex;
        setPreNextIndex();
    }

    public void setDefaultScrollAction(int defaultScrollAction) {
        this.defaultScrollAction = defaultScrollAction;
        scrollAction = defaultScrollAction;
    }

    public void setTurnOverLimitValue(float turnOverLimitValue) {
        this.turnOverLimitValue = turnOverLimitValue;
    }

    public void setDelayMillis(long mDelayMillis) {
        this.mDelayMillis = mDelayMillis;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public void setListener(ItemClickListener listener) {
        this.listener = listener;
    }

    public BannerView(Context context) {
        this(context, null);
    }

    public BannerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BannerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        getViewTreeObserver().addOnGlobalLayoutListener(this);
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.BannerView, defStyleAttr, 0);
        int N = a.getIndexCount();
        for (int i = 0; i < N; i++) {
            if (a.getIndex(i) == R.styleable.BannerView_DefaultScrollAction) {
                scrollAction = a.getInt(a.getIndex(i), AUTO_SCROLL_TO_LEFT);
            } else if (a.getIndex(i) == R.styleable.BannerView_TurnOverLimitValue) {
                turnOverLimitValue = a.getFloat(a.getIndex(i), getScreenWidth(getContext()) * 1.0f * 2 / 3);
            }
        }
        a.recycle();
        paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        paint.setColor(Color.parseColor("#FF0000"));
        paint.setTextSize(100);
        paint.setTypeface(Typeface.DEFAULT_BOLD);

        //初始化nextIndex\preIndex
        //        setPreNextIndex();

        final float density = context.getResources().getDisplayMetrics().density;
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledPagingTouchSlop();
        mMinimumVelocity = (int) (MIN_FLING_VELOCITY * density);
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //        if () {//初始化，模拟假数据
        //            for (int i = 0; i < images.length; i++) {
        //                addBitmap(getContext(), images[i], bitmaps);
        //            }
        //        }
        if (bitmaps.size() > 0) {
            // 画暂停的图片
            if (animator == null) {
                canvas.drawBitmap(bitmaps.get(currentIndex), 0, 0, paint);
            } else {
                if (!animator.isRunning()) {
                    canvas.drawBitmap(bitmaps.get(currentIndex), 0, 0, paint);
                }
            }
            getHandleBitmaps(bitmaps);
            // Log.v("shan", "currentIndex:" + currentIndex + ",enterIndex:" + enterIndex + ",animatedValue:" + animatedValue);
            if (mIsBeingDragged) {
                // 向左滑动
                if (scrollAction == AUTO_SCROLL_TO_LEFT) {
                    // 向左滑动
                    drawBitmap(canvas, animatedValue - mViewWidth, animatedValue);
                } else if (scrollAction == AUTO_SCROLL_TO_RIGHT) {
                    // 向右滑动
                    drawBitmap(canvas, animatedValue, animatedValue - mViewWidth);
                }
            } else {
                if (animator == null) {
                    if (scrollAction == AUTO_SCROLL_TO_LEFT) {
                        // 向左滑动
                        startAnimator(mViewWidth, 0, duration, mDelayMillis);
                    } else if (scrollAction == AUTO_SCROLL_TO_RIGHT) {
                        // 向右滑动
                        startAnimator(0, mViewWidth, duration, mDelayMillis);
                    }
                } else {
                    if (animator.isRunning()) {
                        //绘制
                        // 向左滑动
                        if (scrollAction == AUTO_SCROLL_TO_LEFT) {
                            // 向左滑动
                            drawBitmap(canvas, animatedValue - mViewWidth, animatedValue);
                        } else if (scrollAction == AUTO_SCROLL_TO_RIGHT) {
                            // 向右滑动
                            drawBitmap(canvas, animatedValue, animatedValue - mViewWidth);
                        }
                    } else {
                        if (scrollAction == AUTO_SCROLL_TO_LEFT) {
                            // 向左滑动
                            startAnimator(mViewWidth, 0, duration, mDelayMillis);
                        } else if (scrollAction == AUTO_SCROLL_TO_RIGHT) {
                            // 向右滑动
                            startAnimator(0, mViewWidth, duration, mDelayMillis);
                        }
                    }
                }
            }
        }
    }

    private void drawBitmap(Canvas canvas, float currentBitmapLeft, float enterBitmapLift) {
        canvas.drawBitmap(currentBitmap, currentBitmapLeft, 0, paint);
        canvas.drawBitmap(enterBitmap, enterBitmapLift, 0, paint);
        if (BuildConfig.DEBUG) {
            canvas.drawText(currentIndex + "", (enterBitmapLift) / 2, mViewHeight / 2, paint);
            canvas.drawText(enterIndex + "", (mViewWidth - currentBitmapLeft) / 2, mViewHeight / 2, paint);
        }
    }

    private void getHandleBitmaps(ArrayList<Bitmap> bitmaps) {
        if (null != bitmaps && !bitmaps.isEmpty()) {
            if (scrollAction == AUTO_SCROLL_TO_LEFT) {
                enterIndex = currentIndex + 1;
                if (enterIndex >= bitmaps.size()) {
                    enterIndex = 0;
                }
            } else if (scrollAction == AUTO_SCROLL_TO_RIGHT) {
                enterIndex = currentIndex - 1;
                if (enterIndex < 0) {
                    enterIndex = bitmaps.size() - 1;
                }
            }
            enterBitmap = bitmaps.get(enterIndex);
            currentBitmap = bitmaps.get(currentIndex);
        }
    }

    private void startAnimator(float start, float end, long duration, long delayMillis) {
        if (animator == null) {
            animator = ValueAnimator.ofFloat(start, end);
            animator.setDuration(duration);
            //animator.setInterpolator(new LinearInterpolator());
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {//只要animator.cancel();都会调用此方法
                    if (!mIsBeingTouch) {
                        currentIndex = enterIndex;
                        setPreNextIndex();
                        /**
                         * 只有自动动画完毕才会被重置scrollAction
                         * 当scrollAction==AUTO_SCROLL_TO_RIGHT时，并且再次触发点击事件animator被取消
                         * 则此处scrollAction不会被赋值，由onTouchEvent中处理
                         */
                        if (scrollAction != defaultScrollAction && isAutoScrollToRight) {
                            scrollAction = defaultScrollAction;
                            isAutoScrollToRight = false;
                        }
                        invalidate();
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }
            });
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    animatedValue = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            if (!animator.isRunning()) {
                animator.setStartDelay(delayMillis);
                if (animator.getStartDelay() == mDelayMillis) {
                    startTime = System.currentTimeMillis();
                }
                animator.start();
            }
        } else {//动画取消后再运行，会重置一切animator的参数
            if (!animator.isRunning()) {
                if (null == propertyValuesHolder) {
                    propertyValuesHolder = PropertyValuesHolder.ofFloat("x", start, end);
                } else {
                    propertyValuesHolder.setFloatValues(start, end);
                }
                animator.setValues(propertyValuesHolder);
                animator.setDuration(duration);
                animator.setStartDelay(delayMillis);// 这一行代码不能再判断条件后执行
                if (animator.getStartDelay() == mDelayMillis) {
                    startTime = System.currentTimeMillis();
                }
                animator.start();
            }
        }
    }

    /**
     * 跟随currentIndex改变
     */
    private void setPreNextIndex() {
        if (bitmaps.size() > 0) {
            nextIndex = currentIndex + 1;
            preIndex = currentIndex - 1;
            if (nextIndex >= bitmaps.size()) {
                nextIndex = 0;
            }
            if (preIndex < 0) {
                preIndex = bitmaps.size() - 1;
            }
        }
    }

    public BannerView setBitmaps(ArrayList<Bitmap> bitmaps) {
        if (bitmaps != null && bitmaps.size() > 0) {
            if (this.bitmaps.size() > 0) {
                if (animator != null) {
                    animator.cancel();
                }
                this.bitmaps.clear();
            }
            this.bitmaps.addAll(bitmaps);
        }
        return this;
    }

    public void start() {
        invalidate();
    }

    //    public BannerView addBitmap(int resId) {
    //        addBitmap(getContext(), resId);
    //        return this;
    //    }
    //
    //    public BannerView addBitmap(String url) {
    //        addBitmap(getContext(), url);
    //        return this;
    //    }
    //
    //    private void addBitmap(Context context, int resId) {
    //        Glide.with(context)
    //                .load(resId)
    //                .asBitmap()
    //                .dontAnimate()
    //                .into(new SimpleTarget<Bitmap>(mViewWidth, mViewHeight) {
    //                    @Override
    //                    public void onResourceReady(Bitmap resource, GlideAnimation glideAnimation) {
    //                        if (resource.getHeight() > 0) {
    //                            Bitmap bitmap = Bitmap.createScaledBitmap(resource, mViewWidth, mViewHeight, true);
    //                            bitmaps.add(bitmap);
    //                        }
    //                    }
    //                });
    //    }
    //
    //    private void addBitmap(Context context, String url) {
    //        Glide.with(context)
    //                .load(url)
    //                .asBitmap()
    //                .dontAnimate()
    //                .into(new SimpleTarget<Bitmap>(mViewWidth, mViewHeight) {
    //                    @Override
    //                    public void onResourceReady(Bitmap resource, GlideAnimation glideAnimation) {
    //                        if (resource.getHeight() > 0) {
    //                            Bitmap bitmap = Bitmap.createScaledBitmap(resource, mViewWidth, mViewHeight, true);
    //                            bitmaps.add(bitmap);
    //                        }
    //                    }
    //                });
    //    }

    @Override
    public void onGlobalLayout() {
        if (getHeight() > 0 && getWidth() > 0) {
            mViewHeight = getHeight();
            mViewWidth = getWidth();
            turnOverLimitValue = mViewWidth * 1.0f * 2 / 3;
            getViewTreeObserver().removeOnGlobalLayoutListener(this);
        }
    }

    private DisplayMetrics getDisplayMetrics(Context context) {
        DisplayMetrics displaymetrics = new DisplayMetrics();
        ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(displaymetrics);
        return displaymetrics;
    }

    private float getScreenWidth(Context context) {
        return getDisplayMetrics(context).widthPixels;
    }

    /**
     * TODO
     * 将图片的滑动状态进行细分：
     * 自动向右滑动
     * 自动向左滑动
     * 手势向左滑动
     * 当前图片变为下一张图片（currentIndex+1）
     * 当前图片不改变
     * 手势向右滑动
     * 当前图片不改变
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmaps.size() == 0) {
            return false;
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                final int index = MotionEventCompat.getActionIndex(event);
                mActivePointerId = event.getPointerId(index);
                mIsBeingTouch = true;
                cancelTime = 0;//初始化cancelTime
                moveX = 0;//初始化moveX
                lastRawX = event.getRawX();
                rawX = event.getRawX();
                surplusDuration = animator.getDuration() - animator.getCurrentPlayTime();
                if (animator.isRunning()) {
                    // 取消动画，并记录剩余时间，开启的时候
                    isAutoScrollToRight = false;
                    animator.cancel();
                } else {
                    isAutoScrollToRight = false;
                    cancelTime = System.currentTimeMillis();
                    animator.cancel();
                }
                if (surplusDuration <= 0 || surplusDuration >= animator.getDuration()) {
                    surplusDuration = animator.getDuration();
                }
                if (surplusDuration == animator.getDuration()) {
                    animatedValue = mViewWidth;
                }
                if (scrollAction == AUTO_SCROLL_TO_RIGHT) {
                    /**
                     * 向右滑动的过程中，触发点击事件，而引起的相关bug处理
                     *          修改currentIndex,修改scrollAction为AUTO_SCROLL_TO_LEFT
                     *          修改animatedValue（自动修正）,修改surplusDuration（自动修正）
                     *          由animatedValue的值是否超过屏幕的2/3再次判定是否向左滑动
                     */
                    currentIndex--;
                    if (currentIndex < 0) {
                        currentIndex = bitmaps.size() - 1;
                    }
                    setPreNextIndex();
                    scrollAction = AUTO_SCROLL_TO_LEFT;
                }
                tempIndex = currentIndex;//手势操作时，临时保存currentIndex的值
                lastAnimatedValue = animatedValue;
                break;
            case MotionEvent.ACTION_MOVE:
                mIsBeingDragged = true;
                float currentRawX = event.getRawX();
                moveX += currentRawX - lastRawX;
                animatedValue += currentRawX - lastRawX;
                lastRawX = currentRawX;

                /**
                 * 注0：以下情况只对defaultScrollAction==AUTO_SCROLL_TO_LEFT这种情况处理做处理
                 * 注1：当前图片未完全消失前，都属于当前图片,AUTO_SCROLL_TO_LEFT模式下当前图片一直属于屏幕中左边显示的图片
                 * 注2：图片未显示完全前，点击事件都不可以获取
                 * 注3：当前图片+2（当前图片已消失，并开始显示下一张图片）
                 *
                 * 手势向左滑动
                 *      由当前图片过渡到下一张图片（当前图片+2）的位置
                 *          下一张图片是否超出超出屏幕的1/3，决定是否向右滑动超出的距离，再向左自动循环滚动
                 *      由当前图片过渡到下一张图片（当前图片+1）的位置
                 *          下一张图片有可能超出屏幕宽度的一半，根据相对于的下一张图片是否超出屏幕的1/3，决定是否向右滑动
                 *
                 * 手势向右滑动
                 *      由当前图片过渡到上一张图片（当前图片-1）的位置
                 *          上一张图片是否超出屏幕的2/3，决定是否向右滑动
                 *
                 * Fling
                 *      当手势向左滑动，且速度大于一定值时，取消向右滑动，自动滑动到下一张图片
                 *      当手势向右滑动，且速度大于一定值时，改变向右滑动为向左滑动
                 *
                 * 手势取消或者抬起再对nextIndex或者preIndex赋值
                 */
                if (moveX < 0) {//手势向左滑动
                    if (animatedValue <= 0) {//当前图片已经消失，并开始显示下一张图片
                        //animatedValue<=0 :代表moveX的绝对值已经大于lastAnimatedValue
                        if (nextIndex != currentIndex) {
                            if (preIndex != currentIndex) {
                                currentIndex = nextIndex;
                                animatedValue = mViewWidth;
                            } else {//手势向右滑动后再向左滑动
                                currentIndex++;
                                if (currentIndex >= bitmaps.size()) {
                                    currentIndex = 0;
                                }
                                animatedValue = mViewWidth;
                            }
                        }
                    } else {// 当前图片从新显示出来，或者不曾消失过
                        if (tempIndex != currentIndex && Math.abs(moveX) < lastAnimatedValue) {
                            currentIndex = tempIndex;
                            animatedValue = 0;
                        }

                    }
                } else {//手势向右滑动
                    if (animatedValue >= mViewWidth) {//开始显示出上一张图片
                        //animatedValue>=mViewWidth :代表moveX的绝对值与lastAnimatedValue之和大于width
                        if (preIndex != currentIndex) {
                            currentIndex = preIndex;
                            animatedValue = 0;
                        }
                    } else {//当前图片逐渐显示完整
                        if (tempIndex != currentIndex && Math.abs(moveX) + lastAnimatedValue < mViewWidth) {
                            currentIndex = tempIndex;
                            animatedValue = mViewWidth;
                        }
                    }
                }
                Log.v("shan", "moveX:" + moveX);
                Log.v("shan", "enterIndex:" + enterIndex);
                Log.v("shan", "currentIndex:" + currentIndex);
                Log.v("shan", "animatedValue:" + animatedValue);
                Log.v("shan", "lastAnimatedValue:" + lastAnimatedValue);
                Log.v("shan", "-----------------------------------------");
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                setPreNextIndex();//重新设置PreNextIndex
                mIsBeingDragged = false;
                mIsBeingTouch = false;
                if (cancelTime != 0) {//未动画，等待中
                    surplusDelayMillis = mDelayMillis - (cancelTime - startTime);
                } else {//继续动画
                    surplusDelayMillis = 0;
                }
                if (surplusDelayMillis < 0) {
                    surplusDelayMillis = 0;
                }
                if (moveX < 0) {// 手势向左滑动后的取消处理
                    if (tempIndex != currentIndex) {//当前图片已经消失，并开始显示下一张图片
                        if (-moveX > lastAnimatedValue && animatedValue < turnOverLimitValue) {//下一张图片超出屏幕的1/3->向左滑动
                            // 计算剩余时间  surplusDuration
                        } else {// 下一张图片未超出屏幕的1/3->向右滑动
                            scrollAction = AUTO_SCROLL_TO_RIGHT;
                            //计算剩余时间calculate
                        }
                    } else {//当前图片从新显示出来，或者不曾消失过
                        //计算剩余时间calculate
                    }
                } else if (moveX > 0) {// 手势向右滑动后的取消处理
                    if (tempIndex != currentIndex) {//上一张图片已经显示出来了x
                        if (Math.abs(moveX) + lastAnimatedValue >= mViewWidth && animatedValue > turnOverLimitValue) {
                            //上一张图片占据了屏幕的2/3，向右滑动，再向左滑动
                            scrollAction = AUTO_SCROLL_TO_RIGHT;
                            //计算剩余时间calculate
                        } else {// 上一张图片没有占据屏幕的2/3
                            //计算剩余时间calculate
                        }
                    } else {//上一张图片从未显示
                        //计算剩余时间calculate
                    }
                }

                // TODO: 2017/6/9 打断向右滑动后重新设定向右滑动，此处暂时不考虑（不清楚条件是够合理）
                if (!isAutoScrollToRight && animatedValue > turnOverLimitValue && tempIndex == currentIndex) {

                }

                //求伪瞬时速度
                mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                float velocityX = mVelocityTracker.getXVelocity(mActivePointerId);
                if (Math.abs(velocityX) >= mMinimumVelocity && Math.abs(moveX) > mTouchSlop) {
                    if (velocityX > 0) {//向右Flinging
                        if (scrollAction == AUTO_SCROLL_TO_LEFT) {
                            scrollAction = AUTO_SCROLL_TO_RIGHT;
                        }
                    } else {//向左Flinging
                        if (scrollAction == AUTO_SCROLL_TO_RIGHT) {
                            //计算剩余时间
                            scrollAction = AUTO_SCROLL_TO_LEFT;
                        }
                    }
                }

                // 滑动方向已经确定，以下对相关数值进行修正
                // 计算剩余时间  surplusDuration
                surplusDuration = calculateSurplusDuration();

                if (surplusDuration == duration) {
                    if (cancelTime == 0) {//动画中
                        surplusDelayMillis = 0;
                    } else {//等待中
                        //什么都不做
                    }
                } else if (surplusDuration == 0) {
                    // 什么都不做
                } else {
                    surplusDelayMillis = 0;
                }
                if (null != listener && !animator.isRunning()) {// 不执行动画的时候响应点击事件
                    listener.onItemClick(currentIndex);
                }
                if (surplusDuration == animator.getDuration()) {
                    animatedValue = mViewWidth;
                }
                if (scrollAction == AUTO_SCROLL_TO_LEFT) {
                    // 向左滑动
                    startAnimator(animatedValue, 0, surplusDuration, surplusDelayMillis);
                } else if (scrollAction == AUTO_SCROLL_TO_RIGHT) {
                    /**
                     * 向右滑动
                     *      向右滑动完成后，会触发事件，将一切数据复位成向左滑动，会由此触发一个bug
                     *      就是当动画还未完成时，再次触发点击事件，此时会发生数据的跳转，即currentIndex会由1跳转到3
                     * 解决办法 设置标志位
                     *      当条件满足执行以下代码的时候，设置标志位为true，只有动画化结束才设置为false
                     *      当再次触发手势时，根据此标记为判定执行以下动作
                     *          修改currentIndex,修改scrollAction为AUTO_SCROLL_TO_LEFT
                     *          修改animatedValue,修改surplusDuration
                     *          由animatedValue的值是否超过屏幕的2/3再次判定是否向左滑动
                     */
                    isAutoScrollToRight = true;
                    currentIndex++;
                    if (currentIndex >= bitmaps.size()) {
                        currentIndex = 0;
                    }
                    setPreNextIndex();
                    startAnimator(animatedValue, mViewWidth, surplusDuration, surplusDelayMillis);
                }
                if (Math.abs(event.getRawX() - rawX) > 10) {

                } else {//点击

                }
                break;
        }
        return true;
    }

    /**
     * 计算剩余执行动画事件
     */
    private long calculateSurplusDuration() {
        long surplusDuration;
        if (scrollAction == AUTO_SCROLL_TO_RIGHT) {
            surplusDuration = (long) (duration * 1.0f * (mViewWidth - animatedValue) / mViewWidth);
            if (surplusDuration < 0) {
                surplusDuration = 1;
            }
            return surplusDuration;
            //            return 1;
        } else {
            surplusDuration = (long) (duration * 1.0f * animatedValue / mViewWidth);
            if (surplusDuration < 0) {
                surplusDuration = 1;
            }
            return surplusDuration;
            //            return 1;
        }
    }


    private interface ItemClickListener {
        /**
         * 只有不发生滚动的时候才会触发点击事件
         *
         * @param currentIndex
         */
        void onItemClick(int currentIndex);
    }
}
