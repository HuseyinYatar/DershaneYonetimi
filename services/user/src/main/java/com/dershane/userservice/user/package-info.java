/** Student, teacher, guardian and admin profiles; opens their Keycloak accounts. */
@ApplicationModule(allowedDependencies = {"shared", "branch::*", "subject::*"})
package com.dershane.userservice.user;

import org.springframework.modulith.ApplicationModule;
