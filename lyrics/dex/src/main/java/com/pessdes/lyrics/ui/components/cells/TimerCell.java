package com.pessdes.lyrics.ui.components.cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * НА ДАННЫЙ МОМЕНТ НЕ РАБОТАЕТ.
 */
public class TimerCell extends FrameLayout {

    private final CircleView[] circleViews = new CircleView[3];
    private AnimatorSet mainAnimatorSet;
    private static final int ANIMATION_DURATION_PER_STEP = 350;
    private static final float SCALE_FACTOR = 1.3f;
    private static final float TRANSLATION_Y_DISTANCE = -40f;

    private final int gap;

    public TimerCell(Context context) {
        this(context, true);
    }

    public TimerCell(Context context, boolean animate) {
        super(context);
        setWillNotDraw(false);

        gap = AndroidUtilities.dp(4);
        final int startColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);

        for (int i = 0; i < circleViews.length; i++) {
            circleViews[i] = new CircleView(context);
            circleViews[i].setColor(startColor);
            addView(circleViews[i]);
        }

        if (animate) {
            post(this::startAnimation);
        }
    }

    public void startAnimation() {
        if (false/*mainAnimatorSet != null && mainAnimatorSet.isPaused()*/) {
            mainAnimatorSet.resume();
        }
        else {
            final int startColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
            final int endColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);

            Animator anim1 = createCircleAnimator(circleViews[0], startColor, endColor);
            Animator anim2 = createCircleAnimator(circleViews[1], startColor, endColor);
            Animator anim3 = createCircleAnimator(circleViews[2], startColor, endColor);

            mainAnimatorSet = new AnimatorSet();
            mainAnimatorSet.playSequentially(anim1, anim2, anim3);

            mainAnimatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (isAttachedToWindow()) {
                        mainAnimatorSet.start();
                    }
                }
            });

            mainAnimatorSet.start();
        }
    }

    public void stopAnimation() {
        if (mainAnimatorSet != null) {
            mainAnimatorSet.removeAllListeners();
            mainAnimatorSet.cancel();
            mainAnimatorSet = null;
        }
    }

    public void pauseAnimation() {
        if (mainAnimatorSet != null) {
            mainAnimatorSet.pause();
        }
    }

    private Animator createCircleAnimator(CircleView circle, int startColor, int endColor) {
        ObjectAnimator translationUp = ObjectAnimator.ofFloat(circle, "translationY", 0, AndroidUtilities.dp(TRANSLATION_Y_DISTANCE));
        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(circle, "scaleX", 1f, SCALE_FACTOR);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(circle, "scaleY", 1f, SCALE_FACTOR);
        ValueAnimator colorUp = ValueAnimator.ofObject(new ArgbEvaluator(), startColor, endColor);
        colorUp.addUpdateListener(animator -> circle.setColor((int) animator.getAnimatedValue()));

        AnimatorSet upSet = new AnimatorSet();
        upSet.playTogether(translationUp, scaleUpX, scaleUpY, colorUp);
        upSet.setDuration(ANIMATION_DURATION_PER_STEP);

        ObjectAnimator translationDown = ObjectAnimator.ofFloat(circle, "translationY", AndroidUtilities.dp(TRANSLATION_Y_DISTANCE), 0);
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(circle, "scaleX", SCALE_FACTOR, 1f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(circle, "scaleY", SCALE_FACTOR, 1f);
        ValueAnimator colorDown = ValueAnimator.ofObject(new ArgbEvaluator(), endColor, startColor);
        colorDown.addUpdateListener(animator -> circle.setColor((int) animator.getAnimatedValue()));

        AnimatorSet downSet = new AnimatorSet();
        downSet.playTogether(translationDown, scaleDownX, scaleDownY, colorDown);
        downSet.setDuration(ANIMATION_DURATION_PER_STEP);

        AnimatorSet set = new AnimatorSet();
        set.playSequentially(upSet, downSet);
        return set;
    }
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = 0;
        int desiredHeight = 0;

        if (getChildCount() > 0) {
            View firstChild = getChildAt(0);
            measureChild(firstChild, widthMeasureSpec, heightMeasureSpec);
            desiredHeight = firstChild.getMeasuredHeight();
            int childWidth = firstChild.getMeasuredWidth();
            desiredWidth = (childWidth * circleViews.length) + (gap * (circleViews.length - 1));
        }

        int width = resolveSize(desiredWidth, widthMeasureSpec);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);

        int childSize = (width - (gap * (circleViews.length - 1))) / circleViews.length;
        int childMeasureSpec = MeasureSpec.makeMeasureSpec(childSize, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(childMeasureSpec, childMeasureSpec);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int currentLeft = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            child.layout(currentLeft, 0, currentLeft + childWidth, childHeight);
            currentLeft += childWidth + gap;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

}