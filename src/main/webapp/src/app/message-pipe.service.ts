import { Injectable, NgZone } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class MessagePipeService {

  constructor(private zone: NgZone) { }

  subscribe(queueName: string, callback: (res: any) => void) {
    const w: any = window;
    w.messagePipe.subscribe(queueName, (res: any) => {
      this.zone.run(() => {
        callback(res);
      });
    });
  }

  post(queueName: string, message: any) {
    const w: any = window;
    try {
      w.messagePipe.post(queueName, JSON.stringify(message));
    } catch (e) {
      w.messagePipe.post(queueName, message);
    }
  }
}
