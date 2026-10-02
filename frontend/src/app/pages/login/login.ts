import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from './auth.service'; // 1. Importamos el servicio

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class Login {
  loginForm: FormGroup;
  hidePassword = true;
  isLoading = false; // Para mostrar un estado de carga

  // 2. Inyectamos el servicio en el constructor junto al FormBuilder
  constructor(private fb: FormBuilder, private authService: AuthService) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', Validators.required]
    });
  }

  togglePassword() {
    this.hidePassword = !this.hidePassword;
  }

  onSubmit() {
    if (this.loginForm.valid) {
      this.isLoading = true;
      const { email, password } = this.loginForm.value;

      // 3. Llamamos al servicio
      this.authService.login(email, password).subscribe({
        next: (respuesta) => {
          console.log('Respuesta del servidor:', respuesta);
          this.isLoading = false;
          // Aquí más adelante redirigiremos al usuario a su panel
        },
        error: (err) => {
          console.error('Error al iniciar sesión', err);
          this.isLoading = false;
        }
      });
      
    } else {
      this.loginForm.markAllAsTouched();
    }
  }
}