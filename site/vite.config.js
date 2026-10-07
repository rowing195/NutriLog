import { defineConfig } from 'vite';

// og:image 與 og:url 必須是完整網址，FB／LINE 的爬蟲不認相對路徑。
// Vercel 建置時會給正式網域；本機沒有就留空，退成相對路徑。
const site = process.env.VERCEL_PROJECT_PRODUCTION_URL
  ? `https://${process.env.VERCEL_PROJECT_PRODUCTION_URL}`
  : '';

export default defineConfig({
  plugins: [
    {
      name: 'site-url',
      transformIndexHtml: (html) => html.replaceAll('%SITE_URL%', site),
    },
  ],
});
