import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Home } from './home';

describe('Home', () => {
  let component: Home;
  let fixture: ComponentFixture<Home>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Home],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Home);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display the ESIBuy logo', () => {
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('ESIBuy');
  });

  it('should display the main promotional text', () => {
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain(
      'Compra fácil, segura y desde cualquier lugar'
    );
  });

  it('should display the login link', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const loginLink = compiled.querySelector('a[routerLink="/login"]');

    expect(loginLink).toBeTruthy();
    expect(loginLink?.textContent).toContain('Iniciar sesión');
  });

  it('should display the register link', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const registerLink = compiled.querySelector('a[routerLink="/registro"]');

    expect(registerLink).toBeTruthy();
    expect(registerLink?.textContent).toContain('Registrarse');
  });
});