
// angular 入口程序
import { enableProdMode } from '@angular/core';
import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';

// 应用主module
import { AppModule } from '@app/app.module';

// 环境隔离配置
import { environment } from '@env/environment';

if (environment.production) {
  enableProdMode();
}

// 当你要在浏览器中运行应用时,采用platformBrowserDynamic
platformBrowserDynamic().bootstrapModule(AppModule)
  .catch(err => console.error(err));
