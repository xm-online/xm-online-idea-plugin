import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';

import { AppComponent } from './app.component';
import { SettingsComponent } from './settings/settings.component';
import { AppRoutingModule } from './app-routing.module';
import { RouterModule, Routes } from "@angular/router";
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MatGridListModule } from "@angular/material/grid-list";
import { MatCardModule } from "@angular/material/card";
import { MatButtonModule } from "@angular/material/button";
import { MatIconModule } from "@angular/material/icon";
import { MatListModule } from "@angular/material/list";
import { MatInputModule } from "@angular/material/input";
import { FormsModule } from "@angular/forms";
import { MatSelectModule } from "@angular/material/select";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { FileListDialogComponent } from './file-list-dialog/file-list-dialog.component';
import { EditConditionDialogComponent, RoleManagementComponent } from './role-management/role-management.component';
import { RoleMatrixComponent } from './role-matrix/role-matrix.component';
import { MatTableModule } from "@angular/material/table";
import { DragDropModule } from "@angular/cdk/drag-drop";
import { MatPaginatorModule } from "@angular/material/paginator";
import { MatSortModule } from "@angular/material/sort";
import { SelectCheckAllComponent } from "./role-matrix/app-select-check-all.component";
import { MatDialogModule } from "@angular/material/dialog";
import { Callback } from "./callback";
import { OverlayContainer } from "@angular/cdk/overlay";
import { EntityDiagramComponent } from './entity-diagram/entity-diagram.component';
import { CreateLepDialogComponent } from './create-lep-dialog/create-lep-dialog.component';
import { AutofocusDirective } from './create-lep-dialog/autofocus.directive';

const routes: Routes = [
  { path: 'settings', component: SettingsComponent },
  { path: 'file-list-dialog', component: FileListDialogComponent },
  { path: 'role-management', component: RoleManagementComponent },
  { path: 'role-matrix', component: RoleMatrixComponent },
  { path: 'entity-diagram', component: EntityDiagramComponent },
  { path: 'create-lep-dialog', component: CreateLepDialogComponent },
];

@NgModule({
  declarations: [
    AutofocusDirective,
    AppComponent,
    SettingsComponent,
    FileListDialogComponent,
    RoleManagementComponent,
    RoleMatrixComponent,
    EditConditionDialogComponent,
    SelectCheckAllComponent,
    EntityDiagramComponent,
    CreateLepDialogComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    RouterModule.forRoot(routes, {useHash: true}),
    BrowserAnimationsModule,
    MatGridListModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    DragDropModule,
    MatTableModule,
    MatPaginatorModule,
    FormsModule,
    MatDialogModule,
    MatSortModule
  ],
  exports: [RouterModule],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule {
}
