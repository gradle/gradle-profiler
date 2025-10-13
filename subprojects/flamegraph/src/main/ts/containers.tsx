import type { CSSProperties, ReactNode } from "react"
import styles from "./containers.module.css"

const cx = (...classes: (string | boolean | undefined)[]) =>
    classes.filter(Boolean).join(" ")

interface Props {
    children?: ReactNode
    style?: CSSProperties

    tall?: boolean
    wide?: boolean
}

const toClassName: (props: Props, base: string) => string = (props, base) => {
    return cx(
        styles[base],
        props.tall && styles["tall"],
        props.wide && styles["wide"],
    )
}

/**
 * A flex container that acts as a vertical stack of elements.
 */
export const Stack: React.FC<Props> = (props) => {
    return (
        <div className={toClassName(props, "stack")} style={props.style}>
            {props.children}
        </div>
    )
}

/**
 * A flex container that acts as a horizontal row of elements.
 */
export const Row: React.FC<Props> = (props) => {
    return (
        <div className={toClassName(props, "row")} style={props.style}>
            {props.children}
        </div>
    )
}

/**
 * A flex child that grows to fill available space.
 */
export const Grow: React.FC<Props> = (props) => {
    return (
        <div className={toClassName(props, "grow")} style={props.style}>
            {props.children}
        </div>
    )
}
