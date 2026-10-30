# зависимостей, кроме androidx.autofill, нет

# максимально сжимаем код: меньше классов и методов в памяти
-allowaccessmodification
-repackageclasses ''

# отладочные записи в журнал вырезаются целиком
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
