import { AfterViewInit, Component, NgZone, OnInit, ViewChild } from '@angular/core';
import { MessagePipeService } from "../message-pipe.service";
import { Callback } from "../callback";
import { ActivatedRoute } from "@angular/router";
import { MatInput } from '@angular/material/input';

@Component({
  selector: 'app-create-lep-dialog',
  templateUrl: './create-lep-dialog.component.html',
  styleUrls: ['./create-lep-dialog.component.css']
})
export class CreateLepDialogComponent extends Callback implements AfterViewInit {

  @ViewChild('lepKeyInput') lepKeyInput: MatInput;

  context: LepDialog = {}

  lepKey: string;
  tenant: string;
  generateCodeSnipped: boolean = true;

  constructor(protected messagePipe: MessagePipeService, route: ActivatedRoute) {
    super(messagePipe, route);
  }

  ngAfterViewInit(): void {
    this.lepKeyInput.focus();
  }

  callbackReady() {
    this.messagePipe.subscribe('initData', (res: LepDialog) => {
      console.info('initData', res);
      this.initData(res);
      this.lepKeyInput.focus();
    });
  }

  private initData(res: LepDialog) {
    this.context = res;
    this.tenant = this.context.tenants[0];
  }

  onUpdate() {
    this.messagePipe.post('onUpdate', {
      lepKey: this.lepKey,
      tenant: this.tenant,
      generateCodeSnipped: this.generateCodeSnipped
    });
  }

}

interface LepDialog {
  lepName?: string;
  hasResolver?: boolean;
  isInterface?: boolean;
  tenants?: string[];
}

