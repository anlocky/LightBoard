package com.lightboard.keyboard;

import java.util.Locale;

final class KbText {
    private KbText() { }

    private static final String[] LANGS = {"ru", "en", "uk", "de", "es", "pt", "fr", "it", "tr", "pl", "id", "vi"};

    private static final String[][] T = {
        {"Инкогнито включён", "Incognito on", "Інкогніто ввімкнено", "Inkognito an", "Incógnito activado", "Anônimo ativado", "Incognito activé", "Incognito attivo", "Gizli mod açık", "Tryb incognito włączony", "Penyamaran aktif", "Đã bật ẩn danh"},
        {"Инкогнито выключен", "Incognito off", "Інкогніто вимкнено", "Inkognito aus", "Incógnito desactivado", "Anônimo desativado", "Incognito désactivé", "Incognito disattivato", "Gizli mod kapalı", "Tryb incognito wyłączony", "Penyamaran nonaktif", "Đã tắt ẩn danh"},
        {"Скриншот", "Screenshot", "Скріншот", "Screenshot", "Captura", "Captura de tela", "Capture d'écran", "Screenshot", "Ekran görüntüsü", "Zrzut ekranu", "Tangkapan layar", "Ảnh chụp màn hình"},
        {"Изображение", "Image", "Зображення", "Bild", "Imagen", "Imagem", "Image", "Immagine", "Görsel", "Obraz", "Gambar", "Hình ảnh"},
        {"Распознавание речи недоступно", "Speech recognition unavailable", "Розпізнавання мовлення недоступне", "Spracherkennung nicht verfügbar", "Reconocimiento de voz no disponible", "Reconhecimento de voz indisponível", "Reconnaissance vocale indisponible", "Riconoscimento vocale non disponibile", "Konuşma tanıma kullanılamıyor", "Rozpoznawanie mowy niedostępne", "Pengenalan suara tidak tersedia", "Không có nhận dạng giọng nói"},
        {"Ошибка распознавания: %d", "Recognition error: %d", "Помилка розпізнавання: %d", "Erkennungsfehler: %d", "Error de reconocimiento: %d", "Erro de reconhecimento: %d", "Erreur de reconnaissance : %d", "Errore di riconoscimento: %d", "Tanıma hatası: %d", "Błąd rozpoznawania: %d", "Kesalahan pengenalan: %d", "Lỗi nhận dạng: %d"},
        {"Нужен доступ к микрофону для голосового ввода", "Microphone access is needed for voice input", "Для голосового введення потрібен доступ до мікрофона", "Für die Spracheingabe wird Mikrofonzugriff benötigt", "Se necesita acceso al micrófono para la entrada de voz", "É necessário acesso ao microfone para entrada de voz", "L'accès au microphone est nécessaire pour la saisie vocale", "Serve l'accesso al microfono per l'input vocale", "Sesle giriş için mikrofon erişimi gerekir", "Do wprowadzania głosowego potrzebny jest dostęp do mikrofonu", "Akses mikrofon diperlukan untuk input suara", "Cần quyền truy cập micrô để nhập bằng giọng nói"},
        {"Буфер обмена", "Clipboard", "Буфер обміну", "Zwischenablage", "Portapapeles", "Área de transferência", "Presse-papiers", "Appunti", "Pano", "Schowek", "Papan klip", "Bảng nhớ tạm"},
        {"Очистить", "Clear", "Очистити", "Leeren", "Borrar", "Limpar", "Effacer", "Svuota", "Temizle", "Wyczyść", "Hapus", "Xóa"},
        {"Скопированное появится здесь", "Copied items will appear here", "Скопійоване з'явиться тут", "Kopierte Inhalte erscheinen hier", "Lo copiado aparecerá aquí", "O que você copiar aparecerá aqui", "Les éléments copiés apparaîtront ici", "Gli elementi copiati appariranno qui", "Kopyalananlar burada görünür", "Skopiowane elementy pojawią się tutaj", "Yang disalin akan muncul di sini", "Nội dung đã sao chép sẽ hiện ở đây"},
        {"История буфера выключена", "Clipboard history is off", "Історію буфера вимкнено", "Verlauf der Zwischenablage ist aus", "El historial del portapapeles está desactivado", "O histórico da área de transferência está desativado", "L'historique du presse-papiers est désactivé", "La cronologia degli appunti è disattivata", "Pano geçmişi kapalı", "Historia schowka jest wyłączona", "Riwayat papan klip nonaktif", "Lịch sử bảng nhớ tạm đang tắt"},
        {"Включить", "Turn on", "Увімкнути", "Einschalten", "Activar", "Ativar", "Activer", "Attiva", "Aç", "Włącz", "Aktifkan", "Bật"},
        {"Закреплённые записи хранятся всегда", "Pinned items are kept forever", "Закріплені записи зберігаються завжди", "Angeheftete Einträge bleiben immer erhalten", "Los elementos fijados se conservan siempre", "Itens fixados são mantidos sempre", "Les éléments épinglés sont toujours conservés", "Gli elementi fissati vengono sempre conservati", "Sabitlenen öğeler her zaman saklanır", "Przypięte elementy są przechowywane zawsze", "Item yang disematkan disimpan selamanya", "Mục đã ghim luôn được giữ lại"},
        {"Недавние", "Recent", "Нещодавні", "Zuletzt verwendet", "Recientes", "Recentes", "Récents", "Recenti", "Son kullanılanlar", "Ostatnie", "Terbaru", "Gần đây"},
        {"Перейти", "Go", "Перейти", "Los", "Ir", "Ir", "Aller", "Vai", "Git", "Idź", "Buka", "Đi"},
        {"Найти", "Search", "Знайти", "Suchen", "Buscar", "Buscar", "Chercher", "Cerca", "Ara", "Szukaj", "Cari", "Tìm"},
        {"Отпр.", "Send", "Надісл.", "Senden", "Enviar", "Enviar", "Envoyer", "Invia", "Gönder", "Wyślij", "Kirim", "Gửi"},
        {"Далее", "Next", "Далі", "Weiter", "Sig.", "Avançar", "Suivant", "Avanti", "İleri", "Dalej", "Lanjut", "Tiếp"},
        {"Готово", "Done", "Готово", "Fertig", "Hecho", "Concluído", "OK", "Fine", "Bitti", "Gotowe", "Selesai", "Xong"},
        {"Отменить", "Undo", "Скасувати", "Rückgängig", "Deshacer", "Desfazer", "Annuler", "Annulla", "Geri al", "Cofnij", "Urungkan", "Hoàn tác"},
        {"Вернуть", "Redo", "Повернути", "Wiederholen", "Rehacer", "Refazer", "Rétablir", "Ripristina", "Yinele", "Ponów", "Ulangi", "Làm lại"},
        {"Смайлики", "Smileys", "Смайлики", "Smileys", "Emoticonos", "Carinhas", "Smileys", "Faccine", "Suratlar", "Buźki", "Smiley", "Biểu tượng cảm xúc"},
        {"Жесты и люди", "Gestures & people", "Жести та люди", "Gesten & Personen", "Gestos y personas", "Gestos e pessoas", "Gestes et personnes", "Gesti e persone", "Jestler ve insanlar", "Gesty i ludzie", "Gestur dan orang", "Cử chỉ và người"},
        {"Животные и природа", "Animals & nature", "Тварини і природа", "Tiere & Natur", "Animales y naturaleza", "Animais e natureza", "Animaux et nature", "Animali e natura", "Hayvanlar ve doğa", "Zwierzęta i przyroda", "Hewan dan alam", "Động vật và thiên nhiên"},
        {"Еда и напитки", "Food & drink", "Їжа та напої", "Essen & Trinken", "Comida y bebida", "Comida e bebida", "Nourriture et boissons", "Cibo e bevande", "Yiyecek ve içecek", "Jedzenie i napoje", "Makanan dan minuman", "Đồ ăn và thức uống"},
        {"Спорт и занятия", "Activities & sports", "Спорт і заняття", "Sport & Aktivitäten", "Deportes y actividades", "Esportes e atividades", "Sport et activités", "Sport e attività", "Spor ve etkinlikler", "Sport i aktywności", "Olahraga dan aktivitas", "Thể thao và hoạt động"},
        {"Путешествия", "Travel", "Подорожі", "Reisen", "Viajes", "Viagens", "Voyages", "Viaggi", "Seyahat", "Podróże", "Perjalanan", "Du lịch"},
        {"Предметы", "Objects", "Предмети", "Objekte", "Objetos", "Objetos", "Objets", "Oggetti", "Nesneler", "Przedmioty", "Benda", "Đồ vật"},
        {"Символы", "Symbols", "Символи", "Symbole", "Símbolos", "Símbolos", "Symboles", "Simboli", "Semboller", "Symbole", "Simbol", "Biểu tượng"},
        {"Распознаватель речи не поддерживает этот язык", "The speech recognizer does not support this language", "Розпізнавач мовлення не підтримує цю мову", "Die Spracherkennung unterstützt diese Sprache nicht", "El reconocimiento de voz no admite este idioma", "O reconhecimento de voz não suporta este idioma", "La reconnaissance vocale ne prend pas en charge cette langue", "Il riconoscimento vocale non supporta questa lingua", "Konuşma tanıma bu dili desteklemiyor", "Rozpoznawanie mowy nie obsługuje tego języka", "Pengenalan suara tidak mendukung bahasa ini", "Nhận dạng giọng nói không hỗ trợ ngôn ngữ này"}
    };

    private static int col;
    private static java.util.HashMap<String, String[]> idx;

    static void set(String pref) {
        String l = pref;
        if (l == null || l.equals("auto")) l = Locale.getDefault().getLanguage();
        col = 1;
        for (int i = 0; i < LANGS.length; i++) if (LANGS[i].equals(l)) { col = i; break; }
    }

    static String t(String ru) {
        if (col == 0) return ru;
        java.util.HashMap<String, String[]> m = idx;
        if (m == null) {
            m = new java.util.HashMap<>(T.length * 2);
            for (String[] r : T) m.put(r[0], r);
            idx = m;
        }
        String[] r = m.get(ru);
        return r == null ? ru : r[col];
    }
}
