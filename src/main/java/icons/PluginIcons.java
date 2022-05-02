package icons;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface PluginIcons {

  Icon DEPLOY = getIcon("/icons/deploy.svg");
  Icon TRACK_CHANGES = getIcon("/icons/track-changes.svg");
  Icon STOP_TRACK_CHANGES = getIcon("/icons/stop-track-changes.svg");
  Icon WARNING = getIcon("/icons/warning.svg");
  Icon REFRESH = getIcon("/icons/refresh.svg");
  Icon SETTINGS = getIcon("/icons/settings.svg");

  public static Icon getIcon(String path) {
    return IconLoader.getIcon(path, PluginIcons.class) /*, PluginIcons.class.getClassLoader())*/;
  }

}
