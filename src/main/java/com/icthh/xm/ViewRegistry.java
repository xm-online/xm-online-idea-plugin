package com.icthh.xm;

import com.vaadin.navigator.Navigator;
import com.vaadin.navigator.View;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ViewRegistry {

    private static final Map<String, View> registry = new ConcurrentHashMap<>();

    public static void registry(String viewName, View view) {
        registry.put(viewName, view);
    }

    public static void fillNavigator(Navigator navigator) {
        registry.forEach(navigator::addView);
    }
}
