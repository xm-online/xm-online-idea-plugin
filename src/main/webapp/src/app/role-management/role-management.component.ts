import { AfterViewInit, Component, EventEmitter, Inject, OnInit, Output, ViewChild } from '@angular/core';
import { MatPaginator } from "@angular/material/paginator";
import { MatSort } from "@angular/material/sort";
import { MatTableDataSource } from "@angular/material/table";
import { Subject } from "rxjs";
import { MessagePipeService } from "../message-pipe.service";
import { debounceTime } from "rxjs/operators";
import { ActivatedRoute } from "@angular/router";
import { Callback } from "../callback";
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from "@angular/material/dialog";

@Component({
  selector: 'app-role-management',
  templateUrl: './role-management.component.html',
  styleUrls: ['./role-management.component.css']
})
export class RoleManagementComponent extends Callback implements AfterViewInit {

  @ViewChild(MatPaginator) paginator: MatPaginator;
  @ViewChild(MatSort) sort: MatSort;


  columns: string[] = ['privilegeKey', 'msName', 'enabled', 'reactionStrategy', 'resourceCondition', 'envCondition'];
  dataSource: MatTableDataSource<Permission>;

  filter: string;

  msNames: string[] = ['entity', 'gate']
  selectedRole: Role;
  allRoles: Role[] = [];
  visibleMsNames: string[] = [];

  prevFilter = "";
  prevParsedFilter: any = {};

  filterChanges: Subject<string> = new Subject<string>();
  permissionChange: Subject<string> = new Subject<string>();

  reactionStrategies = [ReactionStrategy.SKIP, ReactionStrategy.EXCEPTION]

  constructor(protected messagePipe: MessagePipeService,
              route: ActivatedRoute,
              private dialog: MatDialog) {
    super(messagePipe, route);

    this.initFilterListener();
    this.initUpdatePermissionListener();
  }

  callbackReady() {
    this.messagePipe.subscribe('initData', (res) => {
      console.info('initData', res);
      this.updateData(res);
    });
  }

  ngAfterViewInit() {
    if (this.dataSource) {
      this.dataSource.paginator = this.paginator;
      this.dataSource.sort = this.sort;
    }
  }

  updateData(res: any) {
    this.msNames = res.msNames;
    this.visibleMsNames = this.msNames;
    this.allRoles = res.allRoles || [];
    if (!this.selectedRole && this.allRoles.length > 0) {
      this.selectedRole = this.allRoles[0];
    }
    this.initDataSource();
  }

  private initFilterListener() {
    this.filterChanges
        .pipe(debounceTime(100))
        .subscribe(model => {
          this.dataSource.filter = JSON.stringify({
            visibleMsName: this.visibleMsNames,
            filter: this.filter
          });
        });
  }

  private initUpdatePermissionListener() {
    this.permissionChange
        .pipe(debounceTime(500))
        .subscribe(model => {
          this.messagePipe.post('updateRole', this.selectedRole);
        });
  }

  private initDataSource() {
    if (!this.selectedRole) {
      return;
    }

    this.dataSource = new MatTableDataSource(this.selectedRole.permissions);
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
    this.dataSource.filterPredicate = (data: Permission, filter: string) => {
      let filterValue = this.prevParsedFilter;
      if (this.prevFilter !== filter) {
        filterValue = JSON.parse(filter);
        this.prevFilter = filter;
        this.prevParsedFilter = filterValue;
      }
      filterValue.filter = filterValue.filter || "";
      return data.privilegeKey.toLowerCase().indexOf(filterValue.filter.toLowerCase()) >= 0 && filterValue.visibleMsName.includes(data.msName);
    };
    this.updateFilter();
  }

  updateFilter() {
    this.filterChanges.next("changed");
  }

  onRoleChange() {
    this.initDataSource();
  }

  updateRole() {
    this.permissionChange.next("changed");
  }

  onConditionEdit(item, fieldName, resources) {
    const dialogRef = this.dialog.open(EditConditionDialogComponent, {
      minWidth: '700px',
      minHeight: '500px',
      data: {
        value: item[fieldName],
        variables: resources.join(', ')
      },
      disableClose: false,
    });
    dialogRef.componentInstance.valueChange.subscribe(value => {
      this.updateField(item, fieldName, value);
    })
    dialogRef.afterClosed().subscribe(() => {
      this.updateField(item, fieldName, dialogRef.componentInstance.value);
    });
  }

  private updateField(item, fieldName, value) {
    item[fieldName] = value;
    if (!item[fieldName] || !item[fieldName].trim()) {
      item[fieldName] = null;
    }
    this.updateRole();
  }
}

@Component({
  selector: 'edit-condition',
  styleUrls: ['./role-management.component.css'],
  template: `
    <mat-form-field style="display: block">
      <textarea rows="20" matInput  [(ngModel)]="value" (ngModelChange)="update()" class="condition-area"></textarea>
    </mat-form-field>
    <br><br>
    <span style="color: #9d9d9d;">
      1. use SpEL functionality<br>
      2. use # before variable<br>
      3. #subject.role, #subject.userKey, #subject.login are available<br>
      4. Available variables: {{variables}}
    </span>
  `
})
export class EditConditionDialogComponent implements OnInit {

  @Output() public valueChange: EventEmitter<string> = new EventEmitter<string>();

  value: string;
  variables: string;

  constructor(
      @Inject(MAT_DIALOG_DATA) public data: any,
      public dialogRef: MatDialogRef<EditConditionDialogComponent>,
  ) {
  }

  ngOnInit(): void {
    this.value = this.data.value;
    this.variables = this.data.variables;
  }

  update() {
    this.valueChange.next(this.value);
  }
}

export interface Role {
  roleKey: string,
  basedOn: string,
  description: string,
  createdDate: string,
  createdBy: string,
  updatedDate: string,
  updatedBy: string,
  permissions: Permission[],
  env: string[]
}


export interface Permission {
  msName: string,
  roleKey: string,
  privilegeKey: string,
  enabled: boolean,
  newPermission: boolean,
  reactionStrategy: ReactionStrategy,
  envCondition: string,
  resourceCondition: string,
  resources: string[];
}

enum ReactionStrategy {
  SKIP='SKIP', EXCEPTION='EXCEPTION'
}
