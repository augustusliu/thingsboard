// 程序引导组件  --- 会在引导过程中把它加载到 DOM 中
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { NgModule } from '@angular/core';

// 引入路由器， 登陆，和首页
import { AppRoutingModule } from './app-routing.module';
import { CoreModule } from '@core/core.module';
import { LoginModule } from '@modules/login/login.module';
import { HomeModule } from '@home/home.module';

import { AppComponent } from './app.component';
import { DashboardRoutingModule } from '@modules/dashboard/dashboard-routing.module';
import { RouterModule, Routes } from '@angular/router';

const routes: Routes = [
  // 自定义的路由，并添加到框架路由中。通配符，如果没有匹配到跳转到home组件中
  { path: '**',
    redirectTo: 'home'   //在app-routing.modules.ts中配置
  }
];

// 框架自带的路由配置
@NgModule({
  imports: [
    RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class PageNotFoundRoutingModule { }


@NgModule({
  // 该模块中唯一的一个模块入口页面
  declarations: [
    AppComponent
  ],
  imports: [
    BrowserModule,
    BrowserAnimationsModule,
    AppRoutingModule,
    CoreModule,
    LoginModule,
    HomeModule,
    DashboardRoutingModule,
    PageNotFoundRoutingModule
  ],
  providers: [],
  bootstrap: [AppComponent]   // 启动时，进入的组件
})
export class AppModule { }
