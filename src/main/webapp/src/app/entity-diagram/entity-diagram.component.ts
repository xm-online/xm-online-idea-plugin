import { AfterViewInit, Component, ElementRef, OnInit, ViewChild } from '@angular/core';

@Component({
  selector: 'app-entity-diagram',
  templateUrl: './entity-diagram.component.html',
  styleUrls: ['./entity-diagram.component.css']
})
export class EntityDiagramComponent implements AfterViewInit {

  @ViewChild('entityDiagram')
  canvasRef: ElementRef<HTMLCanvasElement>;
  canvas: HTMLCanvasElement;

  public context: CanvasRenderingContext2D;

  ngAfterViewInit(): void {
    this.canvas = this.canvasRef.nativeElement;
    this.context = this.canvas.getContext('2d');
    const cvs = this.canvas;
    cvs.width = window.innerWidth;
    cvs.height = window.innerHeight;
  }

}
