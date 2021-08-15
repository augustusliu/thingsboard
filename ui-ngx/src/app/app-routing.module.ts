///
/// Copyright © 2016-2020 The Thingsboard Authors
///
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

const routes: Routes = [
  { path: '',
    redirectTo: 'home',
    pathMatch: 'full',
    data: {
      breadcrumb: {
        skip: true
      }
    }
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
