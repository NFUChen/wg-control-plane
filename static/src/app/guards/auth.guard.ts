import { inject } from '@angular/core';
import { Router, type ActivatedRouteSnapshot, type CanActivateChildFn, type CanActivateFn, type RouterStateSnapshot } from '@angular/router';
import { map } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

function redirectToLoginIfUnauthenticated(state: RouterStateSnapshot) {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureSession().pipe(
    map(ok =>
      ok ? true : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } })
    )
  );
}

export const authGuard: CanActivateFn = (_route: ActivatedRouteSnapshot, state: RouterStateSnapshot) =>
  redirectToLoginIfUnauthenticated(state);

export const authChildGuard: CanActivateChildFn = (_route: ActivatedRouteSnapshot, state: RouterStateSnapshot) =>
  redirectToLoginIfUnauthenticated(state);

export const loginPageGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureSession().pipe(
    map(ok => (ok ? router.createUrlTree(['/servers']) : true))
  );
};
