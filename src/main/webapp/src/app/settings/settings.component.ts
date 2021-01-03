import { AfterViewInit, ChangeDetectorRef, Component, NgZone, OnInit } from '@angular/core';
import { UUID } from "angular2-uuid";
import { MatSelectionListChange } from "@angular/material/list";
import { BehaviorSubject } from "rxjs";



@Component({
  selector: 'app-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.css']
})
export class SettingsComponent {

  envs: EnvironmentSettings[] = [];
  environment: EnvironmentSettings;

  updateModesMap = {};
  updateModes = [];
  branches = [];

  connectionResult = null;

  constructor(private zone: NgZone) {
    let w: any = window;
    w.messagePipe.subscribe('initData', (res) => {
      console.info('initData', res);
      zone.run(() => {
        this.updateData(res);
      });
    });
    w.messagePipe.post('componentReady', 'SettingsComponent ready')

    w.messagePipe.subscribe('connectionResult', (res) => {
      console.info('connectionResult', res);
      zone.run(() => {
        this.connectionResult = res.success;
      });
    });
  }

  private updateData(res) {
    this.envs = res.envs || [];
    if (!this.environment && this.envs.length > 0) {
      this.environment = this.envs[0];
    }

    if (res.updateModes) {
      res.updateModes.forEach(it => this.updateModesMap[it.name] = it.gitMode);
      this.updateModes = Object.keys(this.updateModesMap);
    }
    if (res.branches) {
      this.branches = res.branches;
      this.branches.push('HEAD');
    }
  }

  addEnv() {
    let i = this.envs.length;
    i++
    while(this.envs.filter(it =>  it.name == `env ${i}` ).length > 0) {
      i++
    }

    const element: EnvironmentSettings = {
      id: UUID.UUID(),
      name: `env ${i}`,
      clientId: 'webapp',
      clientPassword: 'webapp',
      updateMode: 'GIT_LOCAL_CHANGES',
      branchName: 'HEAD'
    };
    this.envs.push(element)
    this.onUpdate();
  }

  removeEnv() {
    this.envs = this.envs.filter(it => it.id != this.environment.id);
    this.environment = null;
    this.onUpdate()
  }

  onSelect($event: MatSelectionListChange) {
    this.connectionResult = null;
    if ($event.options.length == 1) {
      let shift = $event.options[0];
      this.environment = shift.value;
    }
  }

  onUpdate() {
    this.connectionResult = null;
    let w: any = window;
    w.messagePipe.post('envsUpdated', JSON.stringify(this.envs));
  }

  testConnection() {
    let w: any = window;
    w.messagePipe.post('testConnection', JSON.stringify(this.environment));
  }
}

interface EnvironmentSettings {
  id?: string;
  name?: string;
  xmUrl?: string;
  xmSuperAdminLogin?: string;
  xmSuperAdminPassword?: string;
  clientId?: string;
  clientPassword?: string;
  updateMode?: string;
  branchName?: string;
  startTrackChangesOnEdit?: boolean;
}
