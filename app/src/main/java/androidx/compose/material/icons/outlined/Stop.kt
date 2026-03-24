/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.material.icons.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Outlined.Stop: ImageVector
    get() {
        if (_stop != null) {
            return _stop!!
        }
        _stop = materialIcon(name = "Outlined.Stop") {
            materialPath {
                moveTo(16.0f, 8.0f)
                verticalLineToRelative(8.0f)
                horizontalLineTo(8.0f)
                verticalLineTo(8.0f)
                horizontalLineToRelative(8.0f)
                moveToRelative(2.0f, -2.0f)
                horizontalLineTo(6.0f)
                verticalLineToRelative(12.0f)
                horizontalLineToRelative(12.0f)
                verticalLineTo(6.0f)
                close()
            }
        }
        return _stop!!
    }

private var _stop: ImageVector? = null
