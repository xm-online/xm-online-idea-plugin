import { AfterViewInit, Component, NgZone, ViewChild, ViewChildren } from '@angular/core';
import { MessagePipeService } from '../message-pipe.service';
import { Callback } from '../callback';
import { ActivatedRoute } from '@angular/router';
import { MatInput } from '@angular/material/input';

@Component({
  selector: 'app-create-lep-dialog',
  templateUrl: './create-lep-dialog.component.html',
  styleUrls: ['./create-lep-dialog.component.css']
})
export class CreateLepDialogComponent extends Callback implements AfterViewInit {

  @ViewChildren(MatInput) lepKeyInput;

  context: LepDialog = {};

  lepKey: string;
  tenant: string;
  generateCodeSnipped = true;

  constructor(protected messagePipe: MessagePipeService, route: ActivatedRoute, private zone: NgZone) {
    super(messagePipe, route);
  }

  ngAfterViewInit(): void {

  }

  callbackReady() {
    this.messagePipe.subscribe('initData', (res: LepDialog) => {
      console.info('initData', res);
      this.initData(res);
    });
  }

  private initData(res: LepDialog) {
    this.context = res;
    this.tenant = this.context.tenants[0];

    setTimeout(() => {
      this.zone.run(() => {
        console.log(this.lepKeyInput.first);
        this.lepKeyInput.first.focus();
        this.onUpdate();
      });
    }, 100);
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

