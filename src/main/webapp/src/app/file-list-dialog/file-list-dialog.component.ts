import {Component, NgZone, OnInit} from '@angular/core';
import { MessagePipeService } from "../message-pipe.service";
import { Callback } from "../callback";
import { ActivatedRoute } from "@angular/router";

@Component({
  selector: 'app-file-list-dialog',
  templateUrl: './file-list-dialog.component.html',
  styleUrls: ['./file-list-dialog.component.css']
})
export class FileListDialogComponent extends Callback {

  changes: FileChange[];
  isForceUpdate: boolean = false;

  constructor(protected messagePipe: MessagePipeService, route: ActivatedRoute) {
    super(messagePipe, route);
  }

  callbackReady() {
    this.messagePipe.subscribe('initData', (res: ChangesFiles) => {
      console.info('initData', res);
      this.initData(res);
    });
    this.messagePipe.subscribe('updateFile', (res: FileChange) => {
      console.info('updateFile', res);
      this.changes.filter(it => it.fileName === res.fileName)
          .forEach(it => {
            Object.keys(res).forEach(key => {
              it[key] = res[key];
            })
          });
    });
  }

  private initData(res: ChangesFiles) {
    this.isForceUpdate = res.isForceUpdate;
    this.changes = res.changes;
    this.sortChanges();
  }

  private sortChanges() {
    this.changes.forEach(it => {
      if (it.editedInThisIteration && !this.isForceUpdate && !it.ignoredFile) {
        it.sortOrder = 1;
      } else if (!it.ignoredFile) {
        it.sortOrder = 2
      } else {
        it.sortOrder = 3;
      }
    })
    this.changes = this.changes.sort((a, b) => a.sortOrder - b.sortOrder);
  }

  ignore(f: FileChange) {
    f.ignoredFile = true;
    this.updateFile(f);
  }

  unignore(f: FileChange) {
    f.ignoredFile = false;
    this.updateFile(f);
  }

  private updateFile(f: FileChange) {
    this.messagePipe.post('updateFile', f);
  }

  navigate(change: FileChange) {
    this.messagePipe.post('navigate', change);
  }

}

interface ChangesFiles {
  isForceUpdate?: boolean;
  changes: FileChange[];
}

interface FileChange {
  fileName: string;
  path: string;
  editedInThisIteration?: boolean;
  newFile?: boolean;
  changesFile?: boolean;
  ignoredFile?: boolean;
  toDelete?: boolean;
  conflict?: boolean;

  sortOrder: number;
}

