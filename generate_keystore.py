#!/usr/bin/env python3
"""
Генератор SSL сертификата для EjectCloud
Создает самоподписанный сертификат на 10 лет
"""

import subprocess
import sys
import os
from pathlib import Path

def run_command(cmd):
    """Выполняет команду и возвращает результат"""
    try:
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        if result.returncode != 0:
            print(f"Ошибка выполнения команды: {cmd}")
            print(f"Stderr: {result.stderr}")
            return False
        return True
    except Exception as e:
        print(f"Ошибка: {e}")
        return False

def check_java():
    """Проверяет наличие Java"""
    return run_command("java -version")

def generate_keystore():
    """Генерирует keystore.p12"""
    keystore_path = Path("keystore.p12")
    
    if keystore_path.exists():
        response = input("Файл keystore.p12 уже существует. Перезаписать? (y/N): ")
        if response.lower() != 'y':
            print("Отменено.")
            return False
        keystore_path.unlink()
    
    # Команда для генерации keystore
    cmd = [
        "keytool", "-genkeypair",
        "-alias", "ejectcloud",
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-storetype", "PKCS12",
        "-keystore", "keystore.p12",
        "-validity", "3650",  # 10 лет
        "-dname", "CN=localhost, O=EjectCloud, C=RU",
        "-storepass", "ejectcloud123",
        "-keypass", "ejectcloud123"
    ]
    
    print("Генерирую SSL сертификат...")
    result = subprocess.run(cmd, capture_output=True, text=True)
    
    if result.returncode == 0:
        print("✅ Сертификат успешно создан: keystore.p12")
        print("🔒 Пароль: ejectcloud123")
        print("⏰ Срок действия: 10 лет")
        return True
    else:
        print("❌ Ошибка создания сертификата:")
        print(result.stderr)
        return False

def create_gitignore():
    """Добавляет keystore.p12 в .gitignore"""
    gitignore_path = Path(".gitignore")
    keystore_line = "keystore.p12"
    
    if gitignore_path.exists():
        content = gitignore_path.read_text()
        if keystore_line not in content:
            with open(gitignore_path, "a") as f:
                f.write(f"\n# SSL Certificate\n{keystore_line}\n")
            print("✅ Добавлено в .gitignore")
    else:
        gitignore_path.write_text(f"# SSL Certificate\n{keystore_line}\n")
        print("✅ Создан .gitignore")

def main():
    print("🔐 EjectCloud SSL Certificate Generator")
    print("=" * 40)
    
    # Проверяем Java
    if not check_java():
        print("❌ Java не найдена. Установите JDK.")
        sys.exit(1)
    
    # Генерируем keystore
    if generate_keystore():
        create_gitignore()
        print("\n🚀 Готово! Теперь можно запускать:")
        print("   mvn spring-boot:run")
        print("\n🌐 Приложение будет доступно:")
        print("   https://localhost:8443")
    else:
        sys.exit(1)

if __name__ == "__main__":
    main()