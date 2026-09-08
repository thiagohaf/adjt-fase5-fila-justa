package com.filajusta.triagem.domain;

/**
 * Faixa fisiologica plausivel de um sinal vital (AD-11): {@code minimo} e
 * {@code maximo} vem de configuracao ({@code filajusta.triagem.limites.*} em
 * application.yml, montada na raiz de composicao), nao de literais
 * espalhados no dominio -- este tipo so opera sobre os limites recebidos.
 *
 * <p>Alem de validar (rede de seguranca de {@code 400}), calcula a subnota
 * {@code 0..1} do algoritmo de Score v1 (Design Notes da spec 2.1): quanto
 * mais longe do centro da faixa, mais proxima de {@code 1}.
 */
public final class FaixaVital {

    private final double minimo;
    private final double maximo;

    public FaixaVital(double minimo, double maximo) {
        if (minimo >= maximo) {
            throw new IllegalArgumentException("minimo deve ser menor que maximo: [" + minimo + ", " + maximo + "]");
        }
        this.minimo = minimo;
        this.maximo = maximo;
    }

    public void exigirDentroDaFaixa(String campo, double valor) {
        // Double.isNaN primeiro: toda comparacao (<, >) com NaN retorna
        // false, entao "valor < minimo || valor > maximo" deixaria um NaN
        // passar silenciosamente e produzir um Score enganoso (subnota 0)
        // em vez de um 400.
        if (Double.isNaN(valor) || valor < minimo || valor > maximo) {
            throw new SinalVitalInvalidoException(campo,
                    campo + " deve estar entre " + minimo + " e " + maximo + " (valor informado: " + valor + ")");
        }
    }

    /**
     * Subnota {@code 0..1} pela distancia normalizada ao centro da faixa.
     * Pressupoe {@code valor} ja validado por {@link #exigirDentroDaFaixa},
     * mas satura em {@code [0,1]} por seguranca.
     */
    public double subnota(double valor) {
        double centro = (minimo + maximo) / 2.0;
        double meiaFaixa = (maximo - minimo) / 2.0;
        double distanciaNormalizada = Math.abs(valor - centro) / meiaFaixa;
        return Math.min(1.0, Math.max(0.0, distanciaNormalizada));
    }

    public double getMinimo() {
        return minimo;
    }

    public double getMaximo() {
        return maximo;
    }
}
