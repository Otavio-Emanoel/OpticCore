# Documento de Especificação de Requisitos: Projeto \"OpticCore\" (Nome Provisório)

## 1. Visão Geral do Produto {#visão-geral-do-produto}

O OpticCore é um aplicativo de câmera Android nativo desenvolvido em Kotlin. Seu objetivo primário é contornar o processamento de sinal de imagem (ISP) padrão de fábrica, fornecendo captura de imagem bruta (RAW/YUV) e aplicando algoritmos customizados de *Tone Mapping* e Colorimetria para emular a assinatura visual de dispositivos premium, garantindo acesso integral a todos os sensores físicos disponíveis no hardware.

## 2. Arquitetura do Sistema {#arquitetura-do-sistema}

A arquitetura será baseada em **MVVM (Model-View-ViewModel)** com injeção de dependência, separando a interface (Jetpack Compose) da lógica de hardware.

O núcleo de hardware utilizará a **Camera2 API**. O fluxo de captura não solicitará JPEGs comprimidos, mas sim buffers de imagem diretos.

## 3. Requisitos Funcionais (RF) {#requisitos-funcionais-rf}

*O que o sistema deve fazer obrigatoriamente.*

| **ID**   | **Nome do Requisito**                   | **Descrição**                                                                                                                                                               | **Prioridade** |
|----------|-----------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|
| **RF01** | **Mapeamento de Hardware**              | O sistema deve varrer o CameraManager e identificar todos os IDs de câmeras lógicas e físicas (Principal 200MP, Ultrawide 0.6x, Macro e Frontal).                           | Crítica        |
| **RF02** | **Alternância de Lentes**               | A interface deve permitir a transição fluida entre as lentes mapeadas no RF01, reiniciando a CameraCaptureSession adequadamente.                                            | Crítica        |
| **RF03** | **Captura Bruta (Raw/YUV)**             | O sistema deve configurar o ImageReader para capturar os frames no formato ImageFormat.YUV_420_888 ou ImageFormat.RAW_SENSOR para evitar o pós-processamento da fabricante. | Crítica        |
| **RF04** | **Controle Manual de Exposição**        | O usuário deve poder ajustar o tempo de exposição (Shutter Speed) e a sensibilidade do sensor (ISO) através da UI.                                                          | Alta           |
| **RF05** | **Processamento de Imagem Customizado** | O sistema deve aplicar matrizes de cor (ColorMatrix) para correção de contraste, saturação e balanço de branco antes da conversão para JPEG.                                | Crítica        |
| **RF06** | **Gestão de Foco**                      | O sistema deve suportar Foco Automático Contínuo (AF) e Foco por Toque (Touch-to-Focus) controlando o CaptureRequest.CONTROL_AF_REGIONS.                                    | Alta           |
| **RF07** | **Gravação em Disco (I/O)**             | O sistema deve converter o buffer processado para JPEG de alta qualidade (100%) e salvá-lo no MediaStore do Android de forma assíncrona.                                    | Crítica        |

## 4. Requisitos Não Funcionais (RNF) {#requisitos-não-funcionais-rnf}

*Como o sistema deve se comportar (Performance, Segurança, Usabilidade).*

| **ID**    | **Nome do Requisito**           | **Descrição**                                                                                                                                                                                   |
|-----------|---------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **RNF01** | **Desempenho de Processamento** | O processamento dos pixels (RF05) não pode bloquear a UI Thread (Main Thread). Deve ser executado em *Background Threads* usando **Kotlin Coroutines** (Dispatchers.Default ou Dispatchers.IO). |
| **RNF02** | **Gerenciamento de Memória**    | O sistema deve alocar e desalocar Bitmaps e buffers do ImageReader estritamente. Vazamentos de memória (Memory Leaks) em matrizes de 12MP a 200MP resultarão em *Out Of Memory (OOM) crashes*.  |
| **RNF03** | **Latência de Captura**         | O tempo entre o clique no obturador (Shutter) e a liberação da UI para uma nova foto (Zero Shutter Lag) não deve exceder 500 milissegundos.                                                     |
| **RNF04** | **Compatibilidade e SDK**       | O código deve ser compilado com minSdkVersion 28 (Android 9) para garantir suporte robusto às features físicas da Camera2 API.                                                                  |
| **RNF05** | **Design de Interface**         | A UI deve ser minimalista, renderizada via Jetpack Compose, sem interrupções visuais sobre o *Viewfinder* (Preview da câmera).                                                                  |

## 5. Fases de Desenvolvimento (Roadmap) {#fases-de-desenvolvimento-roadmap}

Para garantir que o projeto seja entregue com qualidade sênior, a engenharia será dividida em *Sprints* lógicos.

### Fase 1: Fundação e Reconhecimento (Semana 1)

- **Objetivo:** Configuração do ambiente no Linux, permissões e comunicação básica com o hardware.

- **Entregáveis:** \* Projeto estruturado no Android Studio.

  - Algoritmo que lista no terminal (Logcat) as capacidades exatas (Distância Focal, Abertura, Resoluções suportadas) de cada lente do aparelho.

### Fase 2: O Viewfinder e Sessão de Captura (Semana 2)

- **Objetivo:** Fazer a imagem aparecer na tela e gerenciar o ciclo de vida da câmera.

- **Entregáveis:**

  - Implementação de um SurfaceView ou TextureView recebendo os frames da câmera.

  - Lógica para abrir e fechar a câmera sem causar travamentos no sistema operacional.

  - Implementação dos botões de troca de lente (0.6x, 1x, Macro).

### Fase 3: O Motor de Captura (Semana 3)

- **Objetivo:** Extrair a imagem do sensor para a memória RAM.

- **Entregáveis:**

  - Configuração do ImageReader.

  - Gatilho de captura (Shutter) que solicita um frame estático e o converte para um objeto manipulável (Bitmap ou array de bytes).

  - Salvamento temporário na galeria provando que a foto foi tirada.

### Fase 4: O \"Cérebro\" de Processamento (Semana 4)

- **Objetivo:** A matemática da imagem (Onde o aparelho deixa de ser Xiaomi e vira o seu sistema).

- **Entregáveis:**

  - Implementação das Coroutines para processamento pesado.

  - Criação da S-Curve (Curva de tons) para escurecer sombras e proteger realces.

  - Ajuste fino de balanço de branco (Temperatura) para emular a estética alvo.

### Fase 5: Refatoração e UI/UX (Semana 5)

- **Objetivo:** Polimento e prevenção de falhas (Crashlytics).

- **Entregáveis:**

  - Interface final em Jetpack Compose.

  - Tratamento rigoroso de exceções (Ex: O que acontece se o usuário minimizar o app no meio do processamento da foto?).
