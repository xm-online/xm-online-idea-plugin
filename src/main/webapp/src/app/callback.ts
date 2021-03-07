import { ActivatedRoute } from "@angular/router";
import { MessagePipeService } from "./message-pipe.service";
import { OnInit } from "@angular/core";

export abstract class Callback implements OnInit {

    inited: boolean = false;
    ready: boolean = false;

    constructor(protected messagePipe: MessagePipeService, route: ActivatedRoute) {
        const w: any = window;
        w.initCallbacks(route.snapshot.routeConfig.path);
        w.addEventListener('callbackReady', () => {
            this.onReady();
            this.callbackReady();
        });
    }

    ngOnInit(): void {
        this.onReady();
    }

    onReady() {
        if (this.inited && !this.ready) {
            this.messagePipe.post('componentReady', 'ready')
            this.ready = true;
        }
        this.inited = true;
    }

    abstract callbackReady();

}
