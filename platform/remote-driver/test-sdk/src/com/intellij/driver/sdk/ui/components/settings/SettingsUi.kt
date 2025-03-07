package com.intellij.driver.sdk.ui.components.settings

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.WelcomeScreenUI
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.xQuery

fun WelcomeScreenUI.settingsDialog(action: SettingsUiComponent.() -> Unit): SettingsUiComponent =
  x(SettingsUiComponent::class.java) { byTitle("Settings") }.apply(action)

fun IdeaFrameUI.settingsDialog(action: SettingsUiComponent.() -> Unit): SettingsUiComponent =
  x(SettingsUiComponent::class.java) { byTitle("Settings") }.apply(action)

fun WelcomeScreenUI.showSettings() = driver.invokeAction("WelcomeScreen.Settings", now = false)

open class SettingsUiComponent(data: ComponentData): DialogUiComponent(data) {

  val settingsTree: JTreeUiComponent = tree(xQuery { byType("com.intellij.openapi.options.newEditor.SettingsTreeView${"$"}MyTree") })

  fun content(action: UiComponent.() -> Unit): UiComponent =
    x { byType("com.intellij.openapi.options.ex.ConfigurableCardPanel") }.apply(action)
}