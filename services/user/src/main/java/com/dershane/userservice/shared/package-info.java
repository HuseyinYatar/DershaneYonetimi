/**
 * Shared kernel and platform code. Every business module may use it; it never depends on a business
 * module. Open module: all sub-packages are visible to the other modules.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.dershane.userservice.shared;

import org.springframework.modulith.ApplicationModule;
