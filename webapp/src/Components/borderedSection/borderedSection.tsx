import React, {JSX} from "react";
import styles from "./borderedSection.module.scss";

type BorderedSectionProps = {
    icon?: JSX.Element;
    title?: string;
    children: React.ReactNode;
}

export const BorderedSection = ({icon, title, children}: BorderedSectionProps) => {
    return (
        <div className={styles.mainContainer}>
            <div className={styles.header}>
                <div className={styles.headerBorderBefore}></div>
                {(icon || title) && (
                    <div className={styles.headerTitle}>
                        {icon && icon}
                        {title && <span className={styles.title}>{title}</span>}
                    </div>
                )}
                <div className={styles.headerBorderAfter}></div>
            </div>
            <div className={styles.childrenContainer}>{children}</div>
        </div>
    );
}
