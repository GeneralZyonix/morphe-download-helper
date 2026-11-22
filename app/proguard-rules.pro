# Shizuku user services are instantiated by class name in a remote process.
# Keep the class, its public no-arg constructor, and the AIDL stub it extends.
-keep class be.alterra.morphehelper.UpdateUserService { *; }
-keep class be.alterra.morphehelper.IUpdateService { *; }
-keep class be.alterra.morphehelper.IUpdateService$Stub { *; }
-keep class be.alterra.morphehelper.IUpdateService$Stub$Proxy { *; }
