/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.material.motion;

import com.google.android.material.R;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.res.Resources;
import android.os.Build.VERSION_CODES;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.window.BackEvent;
import androidx.annotation.GravityInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import com.google.android.material.animation.AnimationUtils;

/**
 * Utility class for side container views on the left or right edge of the screen (e.g., side sheet,
 * nav drawer, etc.) that support back progress animations.
 *
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public class MaterialSideContainerBackHelper extends MaterialBackAnimationHelper<View> {

  private final float maxScaleXDistanceShrink;
  private final float maxScaleXDistanceGrow;
  private final float maxScaleYDistance;

  public MaterialSideContainerBackHelper(@NonNull View view) {
    super(view);

    Resources resources = view.getResources();
    maxScaleXDistanceShrink =
        resources.getDimension(R.dimen.m3_back_progress_side_container_max_scale_x_distance_shrink);
    maxScaleXDistanceGrow =
        resources.getDimension(R.dimen.m3_back_progress_side_container_max_scale_x_distance_grow);
    maxScaleYDistance =
        resources.getDimension(R.dimen.m3_back_progress_side_container_max_scale_y_distance);
  }

  @RequiresApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
  public void startBackProgress(@NonNull BackEvent backEvent) {
    super.onStartBackProgress(backEvent);
  }

  @RequiresApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
  public void updateBackProgress(@NonNull BackEvent backEvent, @GravityInt int gravity) {
    super.onUpdateBackProgress(backEvent);

    boolean leftSwipeEdge = backEvent.getSwipeEdge() == BackEvent.EDGE_LEFT;
    updateBackProgress(backEvent.getProgress(), leftSwipeEdge, gravity);
  }

  @VisibleForTesting
  @RequiresApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
  public void updateBackProgress(float progress, boolean leftSwipeEdge, @GravityInt int gravity) {
    progress = interpolateProgress(progress);
    boolean leftGravity = checkAbsoluteGravity(gravity, Gravity.LEFT);

    boolean swipeEdgeMatchesGravity = leftSwipeEdge == leftGravity;

    int width = view.getWidth();
    int height = view.getHeight();
    float maxScaleXDeltaShrink = maxScaleXDistanceShrink / width;
    float maxScaleXDeltaGrow = maxScaleXDistanceGrow / width;
    float maxScaleYDelta = maxScaleYDistance / height;

    view.setPivotX(leftGravity ? 0 : width);
    float endScaleXDelta = swipeEdgeMatchesGravity ? maxScaleXDeltaGrow : -maxScaleXDeltaShrink;
    float scaleXDelta = AnimationUtils.lerp(0, endScaleXDelta, progress);
    float scaleX = 1 + scaleXDelta;
    view.setScaleX(scaleX);
    float scaleYDelta = AnimationUtils.lerp(0, maxScaleYDelta, progress);
    float scaleY = 1 - scaleYDelta;
    view.setScaleY(scaleY);

    View childView = getOnlyChildViewOrNull(view);
    if (childView != null) {
      // Preserve the original aspect ratio of the child content, and add content margins.
      childView.setPivotX(leftGravity ? childView.getWidth() : 0);
      childView.setPivotY(0);
      float childScaleX = swipeEdgeMatchesGravity ? 1 - scaleXDelta : 1f;
      float childScaleY = scaleX / scaleY * childScaleX;
      childView.setScaleX(childScaleX);
      childView.setScaleY(childScaleY);
    }
  }

  @RequiresApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
  public void finishBackProgress(
      @NonNull BackEvent backEvent,
      @GravityInt int gravity,
      @Nullable AnimatorListener animatorListener,
      @Nullable AnimatorUpdateListener finishAnimatorUpdateListener) {
    boolean leftSwipeEdge = backEvent.getSwipeEdge() == BackEvent.EDGE_LEFT;
    boolean leftGravity = checkAbsoluteGravity(gravity, Gravity.LEFT);
    float scaledWidth = view.getWidth() * view.getScaleX();
    ObjectAnimator finishAnimator =
        ObjectAnimator.ofFloat(view, View.TRANSLATION_X, leftGravity ? -scaledWidth : scaledWidth);
    if (finishAnimatorUpdateListener != null) {
      finishAnimator.addUpdateListener(finishAnimatorUpdateListener);
    }
    finishAnimator.setInterpolator(new FastOutSlowInInterpolator());
    finishAnimator.setDuration(
        AnimationUtils.lerp(hideDurationMax, hideDurationMin, backEvent.getProgress()));
    finishAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            view.setTranslationX(0);
            updateBackProgress(/* progress= */ 0, leftSwipeEdge, gravity);
          }
        });
    if (animatorListener != null) {
      finishAnimator.addListener(animatorListener);
    }
    finishAnimator.start();
  }

  @RequiresApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
  public void cancelBackProgress() {
    super.onCancelBackProgress();

    AnimatorSet cancelAnimatorSet = new AnimatorSet();
    cancelAnimatorSet.playTogether(
        ObjectAnimator.ofFloat(view, View.SCALE_X, 1),
        ObjectAnimator.ofFloat(view, View.SCALE_Y, 1));

    View childView = getOnlyChildViewOrNull(view);
    if (childView != null) {
      cancelAnimatorSet.playTogether(
          ObjectAnimator.ofFloat(childView, View.SCALE_X, 1),
          ObjectAnimator.ofFloat(childView, View.SCALE_Y, 1));
    }

    cancelAnimatorSet.setDuration(cancelDuration);
    cancelAnimatorSet.start();
  }

  private boolean checkAbsoluteGravity(@GravityInt int gravity, @GravityInt int checkFor) {
    int absoluteGravity =
        GravityCompat.getAbsoluteGravity(gravity, ViewCompat.getLayoutDirection(view));
    return (absoluteGravity & checkFor) == checkFor;
  }

  @Nullable
  private static View getOnlyChildViewOrNull(View view) {
    if (view instanceof ViewGroup) {
      ViewGroup viewGroup = (ViewGroup) view;
      if (viewGroup.getChildCount() == 1) {
        return viewGroup.getChildAt(0);
      }
    }
    return null;
  }
}
