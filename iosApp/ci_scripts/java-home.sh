# Find JDK 17 from either the system or Homebrew's keg-only installation.
quantoot_java_home() {
    local jdk prefix
    if jdk=$(/usr/libexec/java_home -F -v 17 2>/dev/null) && [ -x "$jdk/bin/java" ]; then
        printf '%s\n' "$jdk"
        return
    fi
    if prefix=$(brew --prefix openjdk@17 2>/dev/null); then
        jdk="$prefix/libexec/openjdk.jdk/Contents/Home"
        if [ -x "$jdk/bin/java" ]; then
            printf '%s\n' "$jdk"
            return
        fi
    fi
    return 1
}
