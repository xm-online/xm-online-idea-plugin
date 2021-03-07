import { ActivatedRoute } from "@angular/router";
import { MessagePipeService } from "./message-pipe.service";
import { Component, OnInit } from "@angular/core";

@Component({
    template: ''
})
export abstract class Callback implements OnInit {

    inited: boolean = false;
    ready: boolean = false;

    constructor(protected messagePipe: MessagePipeService, route: ActivatedRoute) {
        const w: any = window;
        w.initCallbacks(route.snapshot.routeConfig.path);
        if (w.callbackReady) {
            this.callbackReady();
            this.onReady();
        } else {
            w.addEventListener('callbackReady', () => {
                this.callbackReady();
                this.onReady();
            });
        }
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
