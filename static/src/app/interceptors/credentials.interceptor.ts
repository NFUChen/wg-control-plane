import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Sends cookies (e.g. JWT after login) on same-origin / proxied API calls.
 */
export const credentialsInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.url.startsWith('/api')) {
    return next(req.clone({ withCredentials: true }));
  }
  return next(req);
};
