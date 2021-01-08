import {Component, OnInit} from '@angular/core';
import { UUID } from "angular2-uuid";
import { MatSelectionListChange } from "@angular/material/list";
import { MessagePipeService } from "../message-pipe.service";

@Component({
  selector: 'app-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.css']
})
export class SettingsComponent implements OnInit {

  envs: EnvironmentSettings[] = [];
  environment: EnvironmentSettings;

  updateModesMap = {};
  updateModes = [];
  branches = [];

  connectionResult = null;

  constructor(private messagePipe: MessagePipeService) {
    messagePipe.subscribe('initData', (res) => {
      console.info('initData', res);
      this.updateData(res);
    });
    messagePipe.subscribe('connectionResult', (res) => {
      console.info('connectionResult', res);
      this.connectionResult = res.success;
    });
  }

  ngOnInit(): void {
     this.messagePipe.post('componentReady', 'SettingsComponent ready')
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
    this.messagePipe.post('envsUpdated', this.envs);
  }

  testConnection() {
    this.messagePipe.post('testConnection', this.environment);
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
