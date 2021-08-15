import { Injectable, NgZone } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, CanActivateChild, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { select, Store } from '@ngrx/store';
import { AppState } from '../core.state';
import { selectAuth } from '../auth/auth.selectors';
import { catchError, map, mergeMap, skipWhile, take } from 'rxjs/operators';
import { AuthState } from '../auth/auth.models';
import { forkJoin, Observable, of } from 'rxjs';
import { enterZone } from '@core/operator/enterZone';
import { Authority } from '@shared/models/authority.enum';
import { DialogService } from '@core/services/dialog.service';
import { TranslateService } from '@ngx-translate/core';
import { UtilsService } from '@core/services/utils.service';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate, CanActivateChild {

  constructor(private store: Store<AppState>,
              private authService: AuthService,
              private dialogService: DialogService,
              private utils: UtilsService,
              private translate: TranslateService,
              private zone: NgZone) {}

  // 自定义方法，获取登陆状态
  getAuthState(): Observable<AuthState> {
    return this.store.pipe(
      select(selectAuth),
      skipWhile((authState) => !authState || !authState.isUserLoaded),
      take(1),
      enterZone(this.zone)
    );
  }

  // 路由防守，返回true/false 框架自带的方法
  canActivate(next: ActivatedRouteSnapshot,
              state: RouterStateSnapshot) {
    return this.getAuthState().pipe(
      mergeMap((authState) => {
        const url: string = state.url;

        let lastChild = state.root;
        const urlSegments: string[] = [];
        if (lastChild.url) {
          urlSegments.push(...lastChild.url.map(segment => segment.path));
        }
        while (lastChild.children.length) {
          lastChild = lastChild.children[0];
          if (lastChild.url) {
            urlSegments.push(...lastChild.url.map(segment => segment.path));
          }
        }
        const path = urlSegments.join('.');
        const publicId = this.utils.getQueryParam('publicId');
        const data = lastChild.data || {};
        const params = lastChild.params || {};
        const isPublic = data.module === 'public';

        if (!authState.isAuthenticated) {
          if (publicId && publicId.length > 0) {
            this.authService.setUserFromJwtToken(null, null, false);
            this.authService.reloadUser();
            return of(false);
          } else if (!isPublic) {
            this.authService.redirectUrl = url;
            // this.authService.gotoDefaultPlace(false);
            return of(this.authService.defaultUrl(false));
          } else {
            if (path === 'login') {
              return forkJoin([this.authService.loadOAuth2Clients()]).pipe(
                map(() => {
                  return true;
                })
              );
            } else {
              return of(true);
            }
          }
        } else {
          if (authState.authUser.isPublic) {
            if (this.authService.parsePublicId() !== publicId) {
              if (publicId && publicId.length > 0) {
                this.authService.setUserFromJwtToken(null, null, false);
                this.authService.reloadUser();
              } else {
                this.authService.logout();
              }
              return of(false);
            }
          }
          const defaultUrl = this.authService.defaultUrl(true, authState, path, params);
          if (defaultUrl) {
            // this.authService.gotoDefaultPlace(true);
            return of(defaultUrl);
          } else {
            const authority = Authority[authState.authUser.authority];
            if (data.auth && data.auth.indexOf(authority) === -1) {
              this.dialogService.forbidden();
              return of(false);
            } else {
              return of(true);
            }
          }
        }
      }),
      catchError((err => { console.error(err); return of(false); } ))
    );
  }

  // 路由防守，子路由是否也进行防守，框架生成
  canActivateChild(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot) {
    return this.canActivate(route, state);
  }
}
