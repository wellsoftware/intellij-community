/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.config;

import com.intellij.openapi.actionSystem.AnActionEvent;

import javax.swing.*;

public class ToggleInvertedBooleanProperty extends ToggleBooleanProperty {
  public ToggleInvertedBooleanProperty(String text,
                                       String description,
                                       Icon icon,
                                       AbstractProperty.AbstractPropertyContainer properties, BooleanProperty property) {
    super(text, description, icon, properties, property);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return !getProperty().get(getProperties()).booleanValue();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    getProperty().set(getProperties(), Boolean.valueOf(!state));
  }

}
