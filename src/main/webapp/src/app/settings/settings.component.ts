import { Component } from '@angular/core';
import { UUID } from "angular2-uuid";
import { MatSelectionListChange } from "@angular/material/list";
import { MessagePipeService } from "../message-pipe.service";
import { Callback } from "../callback";
import { ActivatedRoute } from "@angular/router";
import { ENTER } from '@angular/cdk/keycodes';
import { MatChipInputEvent } from '@angular/material/chips';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';

@Component({
  selector: 'app-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.css']
})
export class SettingsComponent extends Callback {

  enter = ENTER;
  envs: EnvironmentSettings[] = [];
  environment: EnvironmentSettings;

  updateModesMap = {};
  updateModes = [];
  branches = [];
  tenants: string[] = [];
  features: Feature[] = [];
  isConfigProject = false;
  projectType = 'UNKNOWN'
  filteredBranches = [];
  filteredTenant: string[] = [];
  filteredFeature: Feature[] = [];

  connectionResult = null;


  constructor(protected messagePipe: MessagePipeService, route: ActivatedRoute) {
    super(messagePipe, route);
  }

  callbackReady() {
    this.messagePipe.subscribe('initData', (res) => {
      console.info('initData', res);
      this.updateData(res);
    });
    this.messagePipe.subscribe('connectionResult', (res) => {
      console.info('connectionResult', res);
      this.connectionResult = res.success;
    });
    this.messagePipe.subscribe('fileSelected', (res) => {
      console.info('fileSelected', res);
      if (res.isConfigRoot) {
        if (res.id) {
          this.envs.filter(it => it.id == res.id)[0].basePath = res.path;
          this.onUpdate();
        } else {
          this.addNewItem(res.path);
        }
      }
    });
    this.messagePipe.subscribe('setTenants', (res) => {
      console.info('setTenants', res);
      this.tenants = res.tenants;
      this.features = res.features;
      this.filterTenants()
      this.filterFeatures();
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
    if (res.tenants) {
      this.tenants = res.tenants;
    }
    this.isConfigProject = res.isConfigProject;
    this.projectType = res.projectType;
    this.filterTenants()
  }

  addEnv() {
    console.info("On add");

    if (this.projectType === 'MICROSERVICE') {
      this.messagePipe.post('openFileInput', {
        currentPath: null
      });
      return;
    }

    this.addNewItem(null);
  }

  private addNewItem(basePath?: string) {
    let i = this.envs.length;
    i++
    while (this.envs.filter(it => it.name == `env ${i}`).length > 0) {
      i++
    }

    const element: EnvironmentSettings = {
      id: UUID.UUID(),
      name: `env ${i}`,
      clientId: 'webapp',
      clientPassword: 'webapp',
      updateMode: 'GIT_LOCAL_CHANGES',
      isConfigProject: this.isConfigProject,
      basePath: basePath
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
    this.refreshTenantsList();
  }

  onUpdate() {
    this.envs.forEach(it => it.branchName = it.branchName || 'HEAD');
    this.connectionResult = null;
    this.messagePipe.post('envsUpdated', this.envs);
    this.refreshTenantsList();
  }

  private refreshTenantsList() {
    if (this.environment && this.environment.basePath) {
      this.messagePipe.post('getTenants', this.environment);
    }
  }

  testConnection() {
    this.messagePipe.post('testConnection', this.environment);
  }

  changePath(env: EnvironmentSettings) {
    this.messagePipe.post('openFileInput', {
      currentPath: env.basePath,
      id: env.id
    });
  }

  filterBranches(env: EnvironmentSettings, value: string) {
    env.selectedTenants = env.selectedTenants || [];
    this.filteredBranches = this.branches
        .filter(it => !value || value === env.branchName || it.toLowerCase().includes(value.toLowerCase()))
        .filter(it => env.selectedTenants.filter(tenant => it == tenant).length == 0);
  }

  removeTenant(environment: EnvironmentSettings, tenantInput: HTMLInputElement, tenant: string) {
    environment.selectedTenants = environment.selectedTenants || [];
    environment.selectedTenants = environment.selectedTenants.filter(it => it != tenant);
    tenantInput.value = '';
    this.filterTenants(tenantInput.value)
    this.onUpdate();
  }

  addTenant(environment: EnvironmentSettings, tenantInput: HTMLInputElement, $event: MatChipInputEvent) {
    const value = $event.value;
    if ((value || '').trim() && !this.existsTenant(environment, value)) {
      environment.selectedTenants = environment.selectedTenants || [];
      environment.selectedTenants.push(value.trim());
    }
    tenantInput.value = '';
    this.filterTenants(tenantInput.value)
    this.onUpdate();
  }

  private existsTenant(environment: EnvironmentSettings, value: string) {
    environment.selectedTenants = environment.selectedTenants || [];
    return environment.selectedTenants.filter(it => it == value.trim()).length > 0;
  }

  private existsFeature(environment: EnvironmentSettings, value: Feature) {
    environment.selectedFeatures = environment.selectedFeatures || [];
    return environment.selectedFeatures.filter(it => it.name == value.name && it.version == value.version).length > 0;
  }

  selectedTenant(tenantInput: HTMLInputElement, $event: MatAutocompleteSelectedEvent) {
    if (!this.existsTenant(this.environment, $event.option.viewValue)) {
      this.environment.selectedTenants = this.environment.selectedTenants || [];
      this.environment.selectedTenants.push($event.option.viewValue);
    }
    $event.option.deselect();
    tenantInput.value = '';
    this.filterTenants()
    this.onUpdate();
  }

  filterTenants(value: string = '') {
    this.tenants = this.tenants || [];
    this.filteredTenant = value ? this.tenants.filter(it => it.toLowerCase().includes(value.toLowerCase()))
            .filter(it => this.existsTenant(this.environment, it) == false)
        : this.tenants?.filter(it => this.existsTenant(this.environment, it) == false) || [];
  }

  removeFeature(environment: EnvironmentSettings, featureInput: HTMLInputElement, feature: Feature) {
    environment.selectedFeatures = environment.selectedFeatures || [];
    environment.selectedFeatures = environment.selectedFeatures.filter(it => it != feature);
    featureInput.value = '';
    this.filterFeatures()
    this.onUpdate();
  }

  filterFeatures(value: string = '') {
    this.features = this.features || [];
    this.filteredFeature = value ? this.features.filter(it => (`${it.name}/${it.version}`)
            .toLowerCase()
            .includes(value.toLowerCase() || ''))
        .filter(it => this.existsFeature(this.environment, it) == false)
        : this.features?.filter(it => this.existsFeature(this.environment, it) == false) || [];
  }

  selectedFeature(featureInput: HTMLInputElement, $event: MatAutocompleteSelectedEvent) {
    this.environment.selectedFeatures = this.environment.selectedFeatures || [];
    this.environment.selectedFeatures = this.environment.selectedFeatures.filter(it => it.name != $event.option.value.name);
    this.environment.selectedFeatures.push($event.option.value);
    featureInput.value = '';
    $event.option.deselect();
    this.filterFeatures();
    this.onUpdate();
  }
}

interface Feature {
  name: string;
  version: string;
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
  isConfigProject?: boolean;
  basePath?: string;
  selectedTenants?: string[];
  selectedFeatures?: Feature[];
}
