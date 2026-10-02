import { Injectable } from '@angular/core';
import { Observable, of, delay } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  constructor() { }

  // Simula una petición al backend que tarda 1 segundo en responder
  login(email: string, password: string): Observable<any> {
    console.log(`Simulando envío al servidor: ${email}`);
    
    // Aquí más adelante usaremos HttpClient para llamar a Java
    // Por ahora, fingimos que el servidor responde que todo ha ido bien
    return of({ success: true, token: 'fake-jwt-token', role: 'CLIENTE' }).pipe(
      delay(1000) 
    );
  }
}