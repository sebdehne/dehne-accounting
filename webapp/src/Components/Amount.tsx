import React from "react";


export type AmountProps = {
    amountInCents: number;
}

export const Amount = ({amountInCents}: AmountProps) => (<div>
    {
        new Intl.NumberFormat('nb-NO', {
            style: 'currency',
            currency: 'NOK',
            maximumFractionDigits: 2,
        }).format((amountInCents) / 100).replace("kr", "")
    }
</div>)
