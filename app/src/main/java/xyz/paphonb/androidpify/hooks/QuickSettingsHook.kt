package xyz.paphonb.androidpify.hooks

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import de.robv.android.xposed.IXposedHookInitPackageResources
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.findAndHookConstructor
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import de.robv.android.xposed.callbacks.XC_LoadPackage
import xyz.paphonb.androidpify.MainHook
import xyz.paphonb.androidpify.R
import xyz.paphonb.androidpify.aosp.StatusIconContainer
import xyz.paphonb.androidpify.utils.*


/*
 * Copyright (C) 2018 paphonb@xda
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

object QuickSettingsHook : IXposedHookLoadPackage, IXposedHookInitPackageResources {

    lateinit var classLoader: ClassLoader

    private val classQSContainerImpl by lazy { XposedHelpers.findClass("com.android.systemui.qs.QSContainerImpl", classLoader) }
    private val classQuickStatusBarHeader by lazy { XposedHelpers.findClass("com.android.systemui.qs.QuickStatusBarHeader", classLoader) }
    private val classQSFooter by lazy { XposedHelpers.findClass("com.android.systemui.qs.QSFooter", classLoader) }
    private val classQSFooterImpl by lazy { XposedHelpers.findClass("com.android.systemui.qs.QSFooterImpl", classLoader) }
    private val classTouchAnimatorBuilder by lazy { XposedHelpers.findClass("com.android.systemui.qs.TouchAnimator\$Builder", classLoader) }
    private val classDarkIconManager by lazy { XposedHelpers.findClass("com.android.systemui.statusbar.phone.StatusBarIconController\$DarkIconManager", classLoader) }
    private val classTintedIconManager by lazy { XposedHelpers.findClass("com.android.systemui.statusbar.phone.StatusBarIconController\$TintedIconManager", classLoader) }
    private val classStatusBarIconController by lazy { XposedHelpers.findClass("com.android.systemui.statusbar.phone.StatusBarIconController", classLoader) }
    private val classDependency by lazy { XposedHelpers.findClass("com.android.systemui.Dependency", classLoader) }
    private val classQSIconView by lazy { XposedHelpers.findClass("com.android.systemui.plugins.qs.QSIconView", classLoader) }
    private val classQSTileBaseView by lazy { XposedHelpers.findClass("com.android.systemui.qs.tileimpl.QSTileBaseView", classLoader) }
    private val classQSTileState by lazy { XposedHelpers.findClass("com.android.systemui.plugins.qs.QSTile\$State", classLoader) }
    private val classSlashImageView by lazy { XposedHelpers.findClass("com.android.systemui.qs.tileimpl.SlashImageView", classLoader) }
    private val classSlashState by lazy { XposedHelpers.findClass("com.android.systemui.plugins.qs.QSTile.SlashState", classLoader) }
    private val classQSTileImpl by lazy { XposedHelpers.findClass("com.android.systemui.qs.tileimpl.QSTileImpl", classLoader) }
    private val classQSAnimator by lazy { XposedHelpers.findClass("com.android.systemui.qs.QSAnimator", classLoader) }
    private val classQSPanel by lazy { XposedHelpers.findClass("com.android.systemui.qs.QSPanel", classLoader) }

    val mSidePaddings by lazy { MainHook.modRes.getDimensionPixelSize(R.dimen.notification_side_paddings) }
    val mCornerRadius by lazy { MainHook.modRes.getDimensionPixelSize(R.dimen.notification_corner_radius) }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != MainHook.PACKAGE_SYSTEMUI) return
        if (!ConfigUtils.notifications.changePullDown) return

        classLoader = lpparam.classLoader

        findAndHookMethod(classQSContainerImpl, "onFinishInflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val qsContainer = param.thisObject as ViewGroup
                        val context = qsContainer.context
                        val ownContext = ResourceUtils.createOwnContext(context)
                        val qsElevation = ownContext.resources.getDimensionPixelSize(R.dimen.qs_background_elevation).toFloat()

                        if (!MainHook.ATLEAST_O_MR1) {
                            qsContainer.removeViewAt(0)
                            XposedHelpers.setIntField(qsContainer, "mGutterHeight", 0)
                        }

                        qsContainer.background = null

                        qsContainer.addView(View(context).apply {
                            id = R.id.quick_settings_background
                            background = getBackground(context)
                            elevation = qsElevation
                            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
                            setMargins(this)
                        }, 0)

                        qsContainer.addView(View(context).apply {
                            id = R.id.quick_settings_status_bar_background
                            background = ColorDrawable(0xFF000000.toInt())
                            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                    ownContext.resources.getDimensionPixelOffset(R.dimen.quick_qs_offset_height))
                        }, 1)

                        qsContainer.addView(View(context).apply {
                            id = R.id.quick_settings_gradient_view
                            background = ownContext.resources.getDrawable(R.drawable.qs_bg_gradient)
                            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                    ownContext.resources.getDimensionPixelOffset(R.dimen.qs_gradient_height)).apply {
                                topMargin = ownContext.resources.getDimensionPixelOffset(R.dimen.quick_qs_offset_height)
                            }
                        }, 2)

                        (XposedHelpers.getObjectField(param.thisObject, "mQSPanel") as View).apply {
                            elevation = qsElevation
                            setMargins(this)
                            (layoutParams as FrameLayout.LayoutParams).topMargin = ownContext
                                    .resources.getDimensionPixelSize(R.dimen.quick_qs_offset_height)
                        }

                        (XposedHelpers.getObjectField(param.thisObject, "mQSFooter") as View).apply {
                            elevation = qsElevation
                            setMargins(this)
                            findViewById<View>(context.resources.getIdentifier(
                                    "expand_indicator", "id", MainHook.PACKAGE_SYSTEMUI)).visibility = View.GONE
                        }

                        (XposedHelpers.getObjectField(param.thisObject, "mQSDetail") as View).apply {
                            elevation = qsElevation
                            background = getBackground(context)
                            setMargins(this)
                        }

                        (XposedHelpers.getObjectField(param.thisObject, "mQSCustomizer") as View).apply {
                            elevation = qsElevation
                            setMargins(this)
                        }

                        (XposedHelpers.getObjectField(param.thisObject, "mHeader") as View).apply {
                            elevation = qsElevation
                        }

                        qsContainer.findViewById<View>(context.resources.getIdentifier("quick_qs_panel", "id", MainHook.PACKAGE_SYSTEMUI)).apply {
                            (layoutParams as ViewGroup.MarginLayoutParams).topMargin = ownContext
                                    .resources.getDimensionPixelSize(R.dimen.quick_qs_top_margin)
                        }

                        // Swap CarrierText with DateView
                        val carrierText = qsContainer.findViewById<View>(context.resources.getIdentifier(
                                "qs_carrier_text", "id", MainHook.PACKAGE_SYSTEMUI))
                        (carrierText.parent as ViewGroup).removeView(carrierText)

                        val datePaddings = ownContext.resources
                                .getDimensionPixelSize(R.dimen.quick_qs_date_padding)
                        val date = qsContainer.findViewById<TextView>(context.resources.getIdentifier(
                                "date", "id", MainHook.PACKAGE_SYSTEMUI))
                        date.setTextColor(Color.WHITE)
                        date.setPadding(datePaddings, datePaddings, datePaddings, datePaddings)
                        (date.parent as ViewGroup).removeView(carrierText)
                        (date.layoutParams as LinearLayout.LayoutParams).apply {
                            width = 0
                            weight = 1f
                        }

                        val footerLayout = qsContainer.findViewById<ViewGroup>(R.id.quick_qs_footer_layout)
                        val footerLeft = footerLayout.getChildAt(0) as ViewGroup
                        footerLeft.removeAllViews()
                        footerLeft.addView(carrierText)

                        qsContainer.findViewById<ViewGroup>(R.id.quick_qs_system_icons)
                                .addView(date, 1)

                        XposedHelpers.callMethod(footerLayout.parent, "updateResources")
                    }
                })

        findAndHookMethod(classQSContainerImpl, "updateExpansion",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        with(param.thisObject as ViewGroup) {
                            val height = bottom - top
                            findViewById<View>(R.id.quick_settings_background).apply {
                                top = (XposedHelpers.getObjectField(param.thisObject, "mQSPanel") as View).top
                                bottom = height
                            }
                            if (!MainHook.ATLEAST_O_MR1) {
                                val elevation = findViewById<View>(R.id.quick_settings_background).elevation
                                (XposedHelpers.getObjectField(param.thisObject, "mQSDetail") as View).elevation = elevation
                                (XposedHelpers.getObjectField(param.thisObject, "mQSFooter") as View).elevation = elevation
                                (XposedHelpers.getObjectField(param.thisObject, "mQSPanel") as View).elevation = elevation
                            }
                        }
                    }
                })

        var intensity = 1f

        findAndHookMethod(classQuickStatusBarHeader, "onFinishInflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val header = param.thisObject as ViewGroup
                        val context = header.context

                        val systemIcons = header.getChildAt(0) as ViewGroup
                        val battery = systemIcons.findViewById<View>(context.resources.getIdentifier(
                                "battery", "id", MainHook.PACKAGE_SYSTEMUI))
                        val foreground = Color.WHITE
                        val background = 0x4DFFFFFF
                        XposedHelpers.callMethod(XposedHelpers.getObjectField(battery, "mDrawable"), "setColors", foreground, background)
                        XposedHelpers.callMethod(battery, "setTextColor", foreground)

                        if (!MainHook.ATLEAST_O_MR1) {
                            systemIcons.layoutParams.height = MainHook.modRes
                                    .getDimensionPixelSize(R.dimen.qs_header_system_icons_area_height)
                        }

                        // Move clock to left side
                        val clock = systemIcons.findViewById<View>(context.resources.getIdentifier(
                                "clock", "id", MainHook.PACKAGE_SYSTEMUI))
                        systemIcons.removeView(clock)
                        systemIcons.addView(clock, 0)
                        // Swap clock padding too
                        clock.setPadding(clock.paddingRight, clock.paddingTop,
                                clock.paddingLeft, clock.paddingBottom)

                        systemIcons.id = R.id.quick_qs_system_icons

                        val quickQsStatusIcons = LinearLayout(context)
                        quickQsStatusIcons.id = R.id.quick_qs_status_icons

                        val ownResources = ResourceUtils.createOwnContext(context).resources
                        quickQsStatusIcons.layoutParams = RelativeLayout.LayoutParams(
                                RelativeLayout.LayoutParams.MATCH_PARENT,
                                ownResources.getDimensionPixelSize(R.dimen.qs_status_icons_height)).apply {
                            addRule(RelativeLayout.BELOW, R.id.quick_qs_system_icons)
                            topMargin = ownResources.getDimensionPixelSize(R.dimen.qs_status_icons_margin_top)
                            bottomMargin = ownResources.getDimensionPixelSize(R.dimen.qs_status_icons_margin_bottom)
                        }
                        header.addView(quickQsStatusIcons, 0)

                        val statusIcons = StatusIconContainer(context, lpparam.classLoader)
                        statusIcons.layoutParams = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.MATCH_PARENT).apply { weight = 1f }
                        quickQsStatusIcons.addView(statusIcons)

                        val ctw = ContextThemeWrapper(context, context.resources.getIdentifier(
                                "Theme.SystemUI", "style", MainHook.PACKAGE_SYSTEMUI))
                        val signalCluster = LayoutInflater.from(ctw).inflate(context.resources.getIdentifier(
                                "signal_cluster_view", "layout", MainHook.PACKAGE_SYSTEMUI),
                                quickQsStatusIcons, false)
                        signalCluster.id = R.id.signal_cluster
                        signalCluster.layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.MATCH_PARENT).apply {
                            marginStart = ownResources.getDimensionPixelSize(R.dimen.signal_cluster_margin_start)
                        }
                        quickQsStatusIcons.addView(signalCluster)

                        val fillColor = fillColorForIntensity(intensity, context)

                        val applyDarkness = XposedHelpers.findMethodExact(classQuickStatusBarHeader, "applyDarkness",
                                Int::class.java, Rect::class.java, Float::class.java, Int::class.java)
                        applyDarkness.invoke(header, R.id.signal_cluster, Rect(), intensity, fillColor)

                        val iconManager = if (MainHook.ATLEAST_O_MR1) {
                            val constructor = XposedHelpers.findConstructorExact(
                                    classTintedIconManager, ViewGroup::class.java)
                            constructor.newInstance(statusIcons).apply {
                                XposedHelpers.callMethod(this, "setTint", fillColor)
                            }
                        } else {
                            val constructor = XposedHelpers.findConstructorExact(
                                    classDarkIconManager, LinearLayout::class.java)
                            constructor.newInstance(statusIcons)
                        }
                        XposedHelpers.setAdditionalInstanceField(header, "mIconManager",
                                iconManager)

                        XposedHelpers.callMethod(header, "updateResources")
                    }
                })

        findAndHookMethod(classQuickStatusBarHeader, "updateResources", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                classTouchAnimatorBuilder.newInstance().apply {
                    val header = param.thisObject as ViewGroup
                    val quickQsStatusIcons = header.findViewById<View>(R.id.quick_qs_status_icons)
                            ?: return
                    val addFloat = XposedHelpers.findMethodExact(classTouchAnimatorBuilder, "addFloat",
                            Object::class.java, String::class.java, FloatArray::class.java)
                    val alphas = FloatArray(2)
                    alphas[0] = 1f
                    alphas[1] = 0f
                    addFloat.invoke(this, quickQsStatusIcons, "alpha", alphas)
                    XposedHelpers.setAdditionalInstanceField(header, "animator", XposedHelpers.callMethod(this, "build"))
                }
            }
        })

        findAndHookMethod(classQuickStatusBarHeader, "setExpansion",
                Float::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                XposedHelpers.getAdditionalInstanceField(param.thisObject, "animator")?.let {
                    XposedHelpers.callMethod(it, "setPosition", param.args[0])
                }
            }
        })

        findAndHookMethod(ViewGroup::class.java, "onAttachedToWindow", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!classQuickStatusBarHeader.isInstance(param.thisObject)) return

                val iconController = XposedHelpers.callStaticMethod(classDependency, "get", classStatusBarIconController)
                XposedHelpers.getAdditionalInstanceField(param.thisObject, "mIconManager")?.let {
                    XposedHelpers.callMethod(iconController, "addIconGroup", it)
                } ?: logE("onAttachedToWindow: mIconManager = null")
            }
        })

        findAndHookMethod(ViewGroup::class.java, "onDetachedFromWindow", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!classQuickStatusBarHeader.isInstance(param.thisObject)) return

                val iconController = XposedHelpers.callStaticMethod(classDependency, "get", classStatusBarIconController)
                XposedHelpers.getAdditionalInstanceField(param.thisObject, "mIconManager")?.let {
                    XposedHelpers.callMethod(iconController, "removeIconGroup", it)
                } ?: logE("onAttachedToWindow: mIconManager = null")
            }
        })

        findAndHookMethod(classQuickStatusBarHeader, "applyDarkness",
                Int::class.java, Rect::class.java, Float::class.java, Int::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                intensity = param.args[2] as Float
                if (param.args[0] as Int != R.id.signal_cluster)
                    param.result = null
            }
        })

        val footerClass = if (MainHook.ATLEAST_O_MR1) classQSFooterImpl else classQSFooter

        findAndHookMethod(footerClass, "createSettingsAlphaAnimator",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        classTouchAnimatorBuilder.newInstance().apply {
                            val footer = param.thisObject as ViewGroup
                            val context = footer.context

                            val carrierTextId = context.resources.getIdentifier(
                                    "qs_carrier_text", "id", MainHook.PACKAGE_SYSTEMUI)
                            val mEdit = XposedHelpers.getObjectField(param.thisObject, "mEdit")
                            val mMultiUserSwitch = XposedHelpers.getObjectField(param.thisObject, "mMultiUserSwitch")
                            val mSettingsContainer = try {
                                XposedHelpers.getObjectField(param.thisObject, "mSettingsContainer")
                            } catch (e: NoSuchFieldError) {
                                XposedHelpers.getObjectField(param.thisObject, "mSettingsButton")
                            }
                            val carrierText = footer.findViewById<View>(carrierTextId)
                            val addFloat = XposedHelpers.findMethodExact(classTouchAnimatorBuilder, "addFloat",
                                    Object::class.java, String::class.java, FloatArray::class.java)
                            val alphas = FloatArray(2)
                            alphas[0] = 0f
                            alphas[1] = 1f
                            addFloat.invoke(this, mEdit, "alpha", alphas)
                            addFloat.invoke(this, mMultiUserSwitch, "alpha", alphas)
                            addFloat.invoke(this, mSettingsContainer, "alpha", alphas)
                            if (carrierText != null)
                                addFloat.invoke(this, carrierText, "alpha", alphas)
                            param.result = XposedHelpers.callMethod(this, "build")
                        }
                    }
                })

        val clearAlarmShowing = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                XposedHelpers.setBooleanField(param.thisObject, "mAlarmShowing", false)
            }
        }
        findAndHookMethod(footerClass, "updateAnimator", Int::class.java, clearAlarmShowing)
        findAndHookMethod(footerClass, "updateAlarmVisibilities", clearAlarmShowing)

        findAndHookMethod(footerClass, "onFinishInflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val footer = param.thisObject as ViewGroup
                        val context = footer.context

                        val layout = LinearLayout(context)
                        layout.id = R.id.quick_qs_footer_layout
                        layout.layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT)

                        footer.getChildAt(0).apply {
                            footer.removeView(this)
                            layout.addView(this)
                            layoutParams = LinearLayout.LayoutParams(layoutParams as ViewGroup.MarginLayoutParams).apply {
                                width = 0
                                weight = 1f
                            }
                        }

                        LayoutInflater.from(ResourceUtils.createOwnContext(context))
                                .inflate(R.layout.qs_pull_handle, layout, true)

                        footer.getChildAt(0).apply {
                            footer.removeView(this)
                            layout.addView(this)
                            layoutParams = LinearLayout.LayoutParams(layoutParams as ViewGroup.MarginLayoutParams).apply {
                                width = 0
                                weight = 1f
                            }
                        }

                        footer.addView(layout)
                    }
                })

        findAndHookConstructor(classQSTileBaseView, Context::class.java,
                classQSIconView, Boolean::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val iconFrame = XposedHelpers.getObjectField(param.thisObject, "mIconFrame") as ViewGroup
                val context = iconFrame.context

                ImageView(context).apply {
                    id = R.id.qs_tile_background
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(MainHook.modRes.getDrawable(R.drawable.ic_qs_circle))
                    iconFrame.addView(this, 0)
                }

                XposedHelpers.setAdditionalInstanceField(param.thisObject, "mCircleColor", 0)
                XposedHelpers.setAdditionalInstanceField(param.thisObject, "mColorActive",
                        context.getColorAccent())
                XposedHelpers.setAdditionalInstanceField(param.thisObject, "mColorDisabled",
                        context.getDisabled(context.getColorAttr(android.R.attr.textColorTertiary)))
            }
        })

        findAndHookMethod(classQSTileBaseView, "handleStateChanged",
                classQSTileState, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val tileBaseView = param.thisObject as ViewGroup
                val bg = tileBaseView.findViewById<ImageView>(R.id.qs_tile_background)

                val circleColor = getCircleColor(tileBaseView,
                        XposedHelpers.getIntField(param.args[0], "state"))
                val mCircleColor = XposedHelpers.getAdditionalInstanceField(tileBaseView, "mCircleColor") as Int
                if (circleColor != mCircleColor) {
                    if (bg.isShown) {
                        val animator = ValueAnimator.ofArgb(mCircleColor, circleColor).apply { duration = 350 }
                        animator.addUpdateListener { bg.imageTintList = ColorStateList.valueOf((it.animatedValue as Int)) }
                        animator.start()
                    } else {
                        bg.imageTintList = ColorStateList.valueOf(circleColor)
                    }
                    XposedHelpers.setAdditionalInstanceField(tileBaseView, "mCircleColor", circleColor)
                }
            }
        })

        findAndHookMethod(classSlashImageView, "setState",
                classSlashState, Drawable::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.args[0] = null
            }
        })

        findAndHookMethod(classQSTileImpl, "getColorForState",
                Context::class.java, Int::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val state = param.args[1] as Int

                if (state == 2) {
                    param.result = Color.WHITE
                }
            }
        })

        findAndHookMethod(classQSAnimator, "onAnimationAtEnd",
                object : XC_MethodHook() {
                    @Suppress("UNCHECKED_CAST")
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val topFiveQs = XposedHelpers.getObjectField(
                                param.thisObject, "mTopFiveQs") as ArrayList<View>
                        topFiveQs.forEach { (it.parent as View).visibility = View.VISIBLE }
                    }
                })

        findAndHookMethod(classQSAnimator, "onAnimationStarted",
                object : XC_MethodHook() {
                    @Suppress("UNCHECKED_CAST")
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!XposedHelpers.getBooleanField(param.thisObject, "mOnFirstPage"))
                            return

                        val topFiveQs = XposedHelpers.getObjectField(
                                param.thisObject, "mTopFiveQs") as ArrayList<View>
                        topFiveQs.forEach { (it.parent as View).visibility = View.INVISIBLE }
                    }
                })

        findAndHookMethod(classQSPanel, "addDivider",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val divider = XposedHelpers.getObjectField(param.thisObject, "mDivider") as View
                        val context = divider.context

                        divider.setBackgroundColor(applyAlpha(divider.alpha,
                                context.getColorAttr(android.R.attr.colorForeground)))
                    }
                })
    }

    private fun getCircleColor(view: ViewGroup, state: Int): Int {
        return when (state) {
            0, 1 -> XposedHelpers.getAdditionalInstanceField(view, "mColorDisabled") as Int
            2 -> XposedHelpers.getAdditionalInstanceField(view, "mColorActive") as Int
            else -> {
                val stringBuilder = StringBuilder()
                stringBuilder.append("Invalid state ")
                stringBuilder.append(state)
                logE(stringBuilder.toString())
                0
            }
        }
    }

    private fun fillColorForIntensity(intensity: Float, context: Context): Int {
        return if (intensity == 0.0f) {
            val id = context.resources.getIdentifier("light_mode_icon_color_dual_tone_fill", "color", MainHook.PACKAGE_SYSTEMUI)
            context.getColor(id)
        } else {
            val id = context.resources.getIdentifier("dark_mode_icon_color_dual_tone_fill", "color", MainHook.PACKAGE_SYSTEMUI)
            context.getColor(id)
        }
    }

    private fun setMargins(view: View) {
        val lp = view.layoutParams as FrameLayout.LayoutParams
        lp.rightMargin = mSidePaddings
        lp.leftMargin = mSidePaddings
    }

    private fun getBackground(context: Context): Drawable {
        val foreground = context.getColorAttr(android.R.attr.colorForeground)
        val ownResources = ResourceUtils.createOwnContext(context).resources
        return if (foreground == Color.WHITE) {
            ownResources.getDrawable(R.drawable.qs_background_primary_dark, context.theme)
        } else {
            ownResources.getDrawable(R.drawable.qs_background_primary, context.theme)
        }
    }

    override fun handleInitPackageResources(resparam: XC_InitPackageResources.InitPackageResourcesParam) {
        if (resparam.packageName != MainHook.PACKAGE_SYSTEMUI) return
        if (!ConfigUtils.notifications.changePullDown) return

        if (MainHook.ATLEAST_O_MR1) {
            resparam.res.setReplacement(MainHook.PACKAGE_SYSTEMUI, "dimen", "qs_header_system_icons_area_height",
                    MainHook.modRes.fwd(R.dimen.qs_header_system_icons_area_height))
        } else {
            resparam.res.setReplacement(MainHook.PACKAGE_SYSTEMUI, "dimen", "qs_gutter_height",
                    MainHook.modRes.fwd(R.dimen.zero))
        }
        resparam.res.setReplacement(MainHook.PACKAGE_SYSTEMUI, "dimen", "status_bar_header_height",
                MainHook.modRes.fwd(R.dimen.quick_qs_total_height))
    }
}