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

  describe('enlaces del inicio (A9)', () => {
    it('el logo es un enlace al inicio', () => {
      const logo = (fixture.nativeElement as HTMLElement).querySelector('a.logo');

      expect(logo?.getAttribute('href')).toBe('/');
      expect(logo?.getAttribute('aria-label')).toBe('ESIBuy, inicio');
    });

    it('«Explorar productos» lleva a iniciar sesión', () => {
      const explorar = (fixture.nativeElement as HTMLElement).querySelector('a.primary-button');

      expect(explorar?.textContent).toContain('Explorar productos');
      expect(explorar?.getAttribute('href')).toBe('/login');
    });

    it('no quedan enlaces vacíos ni botones sin acción', () => {
      const compiled = fixture.nativeElement as HTMLElement;

      expect(compiled.querySelectorAll('a[href="#"]').length).toBe(0);
      expect(compiled.querySelectorAll('button').length).toBe(0);
    });
  });
});
