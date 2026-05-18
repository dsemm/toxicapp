# Toxic Habbo - Busca nativa com backend do site

App Android nativo, sem WebView, que usa `https://atoxic.com.br/api.php` como backend para buscar dados do Habbo/Habbodex.

## Como gerar APK pelo celular

1. Extraia este ZIP.
2. Envie o conteúdo extraído para um repositório no GitHub.
3. Abra a aba **Actions**.
4. Rode **Build Android APK**.
5. Baixe o artifact **ToxicHabbo-backend-debug-apk**.
6. Extraia o artifact e instale `app-debug.apk`.

## Backend

O app espera que `/api.php` exista no site. Uma cópia está em `backend/api.php`.
