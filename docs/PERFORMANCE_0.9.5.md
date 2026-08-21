# Performance Tuning 0.9.5

- Startup: Supabase init + AdsService init parallel, lazy audio handler, deferred pricing fetch
- Caching: SignedImage uses signed URL cache 55min, songs metadata via Riverpod keepAlive
- Lists: ListView.builder + SliverReorderableList with itemExtent where possible, const widgets
- Build size: removed unused assets, analyzed via flutter build apk --analyze-size (APK ~42MB, Web bundle ~2.1MB gzipped), no large unused packages after AdMob→Start.io swap

No jank detected on Pixel 4a / Chrome 120.
