import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./features/dashboard/pages/dashboard-page/dashboard-page.component').then(
        (m) => m.DashboardPageComponent
      )
  },
  {
    path: 'claims',
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/claims/pages/claims-list-page/claims-list-page.component').then(
            (m) => m.ClaimsListPageComponent
          )
      },
      {
        path: 'new',
        loadComponent: () =>
          import('./features/claims/pages/claim-create-page/claim-create-page.component').then(
            (m) => m.ClaimCreatePageComponent
          )
      },
      {
        path: ':id',
        loadComponent: () =>
          import('./features/claims/pages/claim-detail-page/claim-detail-page.component').then(
            (m) => m.ClaimDetailPageComponent
          )
      }
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];
