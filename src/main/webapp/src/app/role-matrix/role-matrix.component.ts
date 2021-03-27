import { AfterViewInit, Component, OnInit, ViewChild } from '@angular/core';
import { MatSort } from "@angular/material/sort";
import { MatPaginator } from "@angular/material/paginator";
import { MessagePipeService } from "../message-pipe.service";
import { MatTableDataSource } from "@angular/material/table";
import { Subject } from "rxjs";
import { debounceTime } from "rxjs/operators";
import { ActivatedRoute, Router } from "@angular/router";
import { Callback } from "../callback";

@Component({
  selector: 'app-role-matrix',
  templateUrl: './role-matrix.component.html',
  styleUrls: ['./role-matrix.component.css']
})
export class RoleMatrixComponent extends Callback implements AfterViewInit {

  @ViewChild(MatPaginator) paginator: MatPaginator;
  @ViewChild(MatSort) sort: MatSort;

  columns: string[] = ['privilegeKey', 'msName'];
  dataSource: MatTableDataSource<PermissionMatrix>;

  filter: string;

  roleMatrix: RoleMatrix;

  msNames: string[] = ['entity', 'gate']
  roles: string[] = ['ROLE_1', 'ROLE_2']
  visibleRoles: string[] = [];
  visibleMsNames: string[] = [];

  prevFilter = "";
  prevParsedFilter: any = {};

  filterChanges: Subject<string> = new Subject<string>();
  permissionChange: Subject<string> = new Subject<string>();

  constructor(protected messagePipe: MessagePipeService, route: ActivatedRoute) {
    super(messagePipe, route);
    this.initFilterListener();
    this.initUpdatePermissionListener();
  }

  callbackReady() {
    this.messagePipe.subscribe('initData', (res) => {
      console.info('initData', res);
      this.updateData(res);
    });
    this.messagePipe.subscribe('updateData', (res) => {
      console.info('updateData', res);
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
    this.roleMatrix = res.roleMatrix;
    this.roles = res.roleMatrix.roles;
    if (this.visibleRoles.length == 0) {
      this.visibleRoles = this.roles;
    }
    this.msNames = res.msNames;
    if (this.visibleMsNames.length == 0) {
      this.visibleMsNames = this.msNames;
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

          this.messagePipe.post('updateMatrix', this.roleMatrix);
        });
  }

  private initDataSource() {
    let permissions = this.roleMatrix.permissions;
    this.roles.forEach(role => {
      permissions.forEach(permission => {
        permission[role] = permission.roles.includes(role);
      });
    })
    this.dataSource = new MatTableDataSource(permissions);
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
    this.dataSource.filterPredicate = (data: PermissionMatrix, filter: string) => {
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

  getColumns() {
    return [...this.columns, ...this.visibleRoles];
  }

  updateFilter() {
    this.filterChanges.next("changed");
  }

  updatePermission(permission, role, $event) {
    if ($event && !permission.roles.includes(role)) {
      permission.roles.push(role);
      console.log(`add ${role} to permission ${permission.privilegeKey} ===> ${permission.roles}`);
    }
    if (!$event && permission.roles.includes(role)) {
      permission.roles = permission.roles.filter(it => it !== role);
      console.log(`remove ${role} from permission ${permission.privilegeKey} ===> ${permission.roles}`);
    }
    this.permissionChange.next("changed");
  }
}

export interface RoleMatrix {
  roles: string[];
  permissions: PermissionMatrix[];
}

export interface PermissionMatrix {
  msName: string;
  privilegeKey: string;
  permissionType?: PermissionType;
  roles: string[]
}

enum PermissionType {
  SYSTEM, TENANT
}


