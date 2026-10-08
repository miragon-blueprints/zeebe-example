package io.miragon.common.architecture.condition;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

public final class InterfaceImplementationConditions {

    private InterfaceImplementationConditions() {
    }

    /**
     * Ensures that a class implements exactly one interface from a specified package.
     * A typical use-case is to enforce that application services implement exactly one inbound port.
     * @param packagePattern The package pattern the interface should reside in
     * @return ArchCondition that can be used in {@code .should(...)}
     */
    public static ArchCondition<JavaClass> implementExactlyOneInterfaceFrom(String packagePattern) {
        return new ArchCondition<>("implement exactly one interface from " + packagePattern) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String clazzName = item.getSimpleName();
                long useCasesOrQueries =
                    item.getInterfaces().stream()
                        .filter(it -> isLocatedInPackage(it, packagePattern))
                        .count();
                if (useCasesOrQueries != 1) {
                    String error = clazzName + " should implement exactly one interface, but implements '" + useCasesOrQueries + "'";
                    events.add(SimpleConditionEvent.violated(item, error));
                } else {
                    events.add(SimpleConditionEvent.satisfied(item, clazzName + " is OK"));
                }
            }
        };
    }

    private static boolean isLocatedInPackage(JavaType type, String packagePattern) {
        return type.toErasure().getPackageName().contains(packagePattern);
    }
}
